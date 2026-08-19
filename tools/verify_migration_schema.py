#!/usr/bin/env python3
"""Audit the v1 -> v2 Room migration against Room's own exported schema.

Room validates a migrated database at runtime and throws if anything differs from the
entities; that is what `MigrationV1ToV2Test` leans on. This script performs the same
comparison ahead of time and reports every discrepancy at once instead of only the first
table Room happens to reject.

For every table in the exported schema it compares, against the DDL that actually runs:

    columns       name, affinity (type), NOT NULL, DEFAULT
    primary key   column list
    indices       columns and uniqueness
    foreign keys  referenced table/columns, ON DELETE, ON UPDATE

"What actually runs" is the union of the v1 DDL (for tables that already existed) and the
migration's own CREATE TABLE / ALTER TABLE ADD COLUMN / CREATE INDEX statements.

Ground truth is the JSON Room's processor emits from the entities, so affinities, defaults
and foreign-key actions come from Room rather than from a re-reading of the entity source.

The v1 side is the DDL in `MigrationV1ToV2Test` -- the only description of schema version 1
in existence, since v1 shipped with `fallbackToDestructiveMigration()` and exported nothing.
That makes this a check on the fixture as much as on the migration, deliberately: a fixture
misdescribing v1 produces exactly the Room validation failure it is meant to catch, and did.

Exits 1 on any discrepancy, and also when no exported schema can be found. CI forces the
codegen that produces it, so an absent schema means the comparison did not happen -- which
must never be reported as success.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SCHEMA_DIR = REPO / "app/schemas"
TARGET_VERSION = 2
MIGRATIONS = REPO / "app/src/main/java/com/warehouse/inventory/data/local/Migrations.kt"
V1_FIXTURE = REPO / "app/src/test/java/com/warehouse/inventory/data/MigrationV1ToV2Test.kt"

AFFINITIES = ("INTEGER", "TEXT", "REAL", "BLOB", "NUMERIC")


# --------------------------------------------------------------------------- helpers

def find_schema() -> Path | None:
    """Locate the exported schema for [TARGET_VERSION], whichever layout Room used.

    Room 2.6 nests exports under a directory named for the database class
    (`app/schemas/<fqcn>/2.json`); older versions wrote `app/schemas/2.json`. Searching
    rather than hardcoding one of them is why this reports a comparison instead of silently
    finding nothing.
    """
    if not SCHEMA_DIR.is_dir():
        return None
    matches = sorted(SCHEMA_DIR.rglob(f"{TARGET_VERSION}.json"))
    return matches[0] if matches else None


def split_top_level(body: str) -> list[str]:
    """Split a CREATE TABLE body on commas that are not inside parentheses."""
    parts, depth, current = [], 0, ""
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += ch
    if current.strip():
        parts.append(current)
    return [p.strip() for p in parts if p.strip()]


def normalise_default(value: str | None) -> str | None:
    """Strip SQL quoting and Room's parenthesised wrapper so the two sides compare."""
    if value is None:
        return None
    v = value.strip()
    while v.startswith("(") and v.endswith(")"):
        v = v[1:-1].strip()
    if len(v) >= 2 and v[0] == v[-1] and v[0] in "'\"":
        v = v[1:-1]
    return v


class TableDdl:
    """Columns, primary key and foreign keys parsed out of DDL for one table."""

    def __init__(self) -> None:
        # column name -> {"affinity", "notNull", "default"}
        self.columns: dict[str, dict] = {}
        self.primary_key: list[str] = []
        # (referenced table, tuple(columns), tuple(referenced columns), onDelete, onUpdate)
        self.foreign_keys: set[tuple] = set()

    def add_definition(self, part: str) -> None:
        upper = part.upper()

        fk = re.match(
            r"FOREIGN\s+KEY\s*\(([^)]*)\)\s*REFERENCES\s+`(\w+)`\s*\(([^)]*)\)(.*)",
            part, re.I | re.S,
        )
        if fk:
            cols = tuple(re.findall(r"`(\w+)`", fk.group(1)))
            ref_cols = tuple(re.findall(r"`(\w+)`", fk.group(3)))
            tail = fk.group(4).upper()
            on_delete = self._action(tail, "DELETE")
            on_update = self._action(tail, "UPDATE")
            self.foreign_keys.add((fk.group(2), cols, ref_cols, on_delete, on_update))
            return

        if upper.startswith("PRIMARY KEY"):
            self.primary_key = list(re.findall(r"`(\w+)`", part))
            return

        col = re.match(r"`(\w+)`\s+(" + "|".join(AFFINITIES) + r")\b(.*)", part, re.I | re.S)
        if not col:
            return
        name, affinity, rest = col.group(1), col.group(2).upper(), col.group(3)
        rest_upper = rest.upper()
        default = None
        dm = re.search(r"DEFAULT\s+('(?:[^']|'')*'|\S+)", rest, re.I)
        if dm:
            default = normalise_default(dm.group(1))
        self.columns[name] = {
            "affinity": affinity,
            "notNull": "NOT NULL" in rest_upper,
            "default": default,
        }
        if "PRIMARY KEY" in rest_upper:
            self.primary_key = [name]

    @staticmethod
    def _action(tail: str, kind: str) -> str:
        m = re.search(rf"ON\s+{kind}\s+(NO\s+ACTION|RESTRICT|SET\s+NULL|SET\s+DEFAULT|CASCADE)", tail)
        return re.sub(r"\s+", " ", m.group(1)) if m else "NO ACTION"


def parse_tables(source: str, keyword: str) -> dict[str, TableDdl]:
    """Parse every `keyword <name> ( ... )` statement in `source`."""
    tables: dict[str, TableDdl] = {}
    for match in re.finditer(keyword + r"\s+`(\w+)`\s*\(", source, re.I):
        depth, i = 1, match.end()
        while i < len(source) and depth:
            if source[i] == "(":
                depth += 1
            elif source[i] == ")":
                depth -= 1
            i += 1
        table = tables.setdefault(match.group(1), TableDdl())
        for part in split_top_level(source[match.end(): i - 1]):
            table.add_definition(part)
    return tables


def parse_alters(source: str, tables: dict[str, TableDdl]) -> None:
    """Fold `ALTER TABLE x ADD COLUMN ...` into the parsed tables."""
    for m in re.finditer(
        r"ALTER TABLE `(\w+)` ADD COLUMN\s+(`\w+`\s+(?:" + "|".join(AFFINITIES) + r")\b[^\"]*)",
        source, re.I,
    ):
        tables.setdefault(m.group(1), TableDdl()).add_definition(m.group(2).strip())


def parse_indices(source: str) -> set[tuple[str, tuple[str, ...], bool]]:
    """(table, columns, unique) for every CREATE INDEX, including built-up statements.

    The source is flattened first because the migration concatenates and interpolates its
    SQL: the seven inventory_transactions indices come from a loop over a column list, and a
    naive per-statement regex misses every one of them.
    """
    flat = source.replace('" +', " ").replace('"', " ")
    found = set()
    for match in re.finditer(
        r"CREATE\s+(UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?`([^`]+)`\s+ON\s+`(\w+)`\s*\(([^)]*)\)",
        flat, re.I,
    ):
        cols = tuple(re.findall(r"`(\w+)`", match.group(4)))
        if cols:
            found.add((match.group(3), cols, bool(match.group(1))))
    loop = re.search(
        r"listOf\(\s*((?:\s*\"\w+\"\s*,?\s*)+)\)\s*\.forEach\s*\{\s*column", source
    )
    if loop and "index_inventory_transactions_" in source:
        for col in re.findall(r'"(\w+)"', loop.group(1)):
            found.add(("inventory_transactions", (col,), False))
    return found


# ------------------------------------------------------------------------------ main

def main() -> int:
    schema_path = find_schema()
    if schema_path is None:
        # Fatal on purpose. CI runs `kspDebugKotlin --rerun-tasks` immediately before this, so
        # a missing export means codegen or this lookup is broken. Either way the migration is
        # unverified, and exiting 0 would turn "not checked" into "checked and fine".
        print(f"FAIL: no {TARGET_VERSION}.json found under {SCHEMA_DIR.relative_to(REPO)} "
              "-- the comparison could not be performed.")
        listing = sorted(p.relative_to(REPO).as_posix() for p in SCHEMA_DIR.rglob("*")) \
            if SCHEMA_DIR.is_dir() else []
        print(f"       directory contents: {listing or '(missing or empty)'}")
        return 1

    print(f"SCHEMA PATH: {schema_path.relative_to(REPO).as_posix()}")
    schema = json.loads(schema_path.read_text(encoding="utf-8"))["database"]
    print(f"SCHEMA VERSION: {schema['version']}  (expected {TARGET_VERSION})")
    if schema["version"] != TARGET_VERSION:
        print(f"FAIL: exported schema is version {schema['version']}, not {TARGET_VERSION}.")
        return 1

    migration_src = MIGRATIONS.read_text(encoding="utf-8")
    fixture_src = V1_FIXTURE.read_text(encoding="utf-8")

    v1 = parse_tables(fixture_src, "CREATE TABLE")
    after = parse_tables(migration_src, "CREATE TABLE IF NOT EXISTS")
    parse_alters(migration_src, after)
    indices = parse_indices(migration_src) | parse_indices(fixture_src)

    dropped = set(re.findall(r"DROP TABLE `(\w+)`", migration_src))
    print(f"TABLES DROPPED BY MIGRATION: {sorted(dropped) or '(none)'}")
    print(f"ENTITIES TO SATISFY: {len(schema['entities'])}\n")

    header = f"{'table':24s} {'cols':>5s} {'idx':>4s} {'fks':>4s}  verdict"
    print(header)
    print("-" * len(header))

    failures: list[str] = []
    for entity in sorted(schema["entities"], key=lambda e: e["tableName"]):
        table = entity["tableName"]
        problems: list[str] = []

        # Merge v1 and post-migration definitions for this table.
        merged = TableDdl()
        for side in (v1.get(table), after.get(table)):
            if side is None:
                continue
            merged.columns.update(side.columns)
            merged.foreign_keys |= side.foreign_keys
            if side.primary_key:
                merged.primary_key = side.primary_key

        if table in dropped:
            problems.append("dropped by the migration but still an entity")
        if not merged.columns:
            problems.append("never created by the v1 DDL or the migration")

        # ---- columns: name, affinity, nullability, default ----
        expected_cols = {f["columnName"]: f for f in entity["fields"]}
        if merged.columns:
            for name, field in sorted(expected_cols.items()):
                actual = merged.columns.get(name)
                if actual is None:
                    problems.append(f"missing column `{name}`")
                    continue
                if actual["affinity"] != field.get("affinity", "").upper():
                    problems.append(
                        f"`{name}` type {actual['affinity']} != schema "
                        f"{field.get('affinity')}"
                    )
                if actual["notNull"] != bool(field.get("notNull")):
                    problems.append(
                        f"`{name}` NOT NULL {actual['notNull']} != schema "
                        f"{bool(field.get('notNull'))}"
                    )
                want_default = normalise_default(field.get("defaultValue"))
                if want_default != actual["default"]:
                    problems.append(
                        f"`{name}` default {actual['default']!r} != schema {want_default!r}"
                    )
            for extra in sorted(set(merged.columns) - set(expected_cols)):
                problems.append(f"unexpected column `{extra}`")

        # ---- primary key ----
        want_pk = list(entity.get("primaryKey", {}).get("columnNames", []))
        if merged.columns and sorted(want_pk) != sorted(merged.primary_key):
            problems.append(f"primary key {merged.primary_key} != schema {want_pk}")

        # ---- indices ----
        want_idx = {
            (table, tuple(i["columnNames"]), bool(i.get("unique")))
            for i in entity.get("indices", [])
        }
        for _, cols, uniq in sorted(want_idx - indices):
            problems.append(
                f"missing {'unique ' if uniq else ''}index on {list(cols)}"
            )

        # ---- foreign keys ----
        want_fks = {
            (
                fk["table"],
                tuple(fk["columns"]),
                tuple(fk["referencedColumns"]),
                re.sub(r"\s+", " ", fk.get("onDelete", "NO ACTION").upper()),
                re.sub(r"\s+", " ", fk.get("onUpdate", "NO ACTION").upper()),
            )
            for fk in entity.get("foreignKeys", [])
        }
        if merged.columns:
            for fk in sorted(want_fks - merged.foreign_keys):
                problems.append(f"missing/incorrect foreign key {fk}")
            for fk in sorted(merged.foreign_keys - want_fks):
                problems.append(f"unexpected foreign key {fk}")

        verdict = "OK" if not problems else "MISMATCH"
        print(f"{table:24s} {len(expected_cols):5d} {len(want_idx):4d} "
              f"{len(want_fks):4d}  {verdict}")
        for problem in problems:
            print(f"{'':24s} {'':5s} {'':4s} {'':4s}    - {problem}")
            failures.append(f"{table}: {problem}")

    print()
    if failures:
        print(f"FAIL: {len(failures)} discrepancy/discrepancies between the migration and "
              "Room's exported schema.")
        return 1
    print("PASS: every table, column, type, nullability, default, primary key, index and "
          "foreign key Room expects in v2 is produced by the migration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
