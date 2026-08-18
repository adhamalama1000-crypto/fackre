package com.warehouse.inventory.ui.navigation

/**
 * Keys passed between destinations through the back stack entry's `SavedStateHandle`.
 *
 * This is how the shared barcode scanner returns a result to whichever form opened it. The
 * scanner is a full destination rather than an embedded camera, so the caller sets nothing
 * up front: the scanner writes the code onto the *previous* back stack entry's handle and
 * pops itself, and the caller's ViewModel — which is scoped to that same entry — observes
 * the key with `savedStateHandle.getStateFlow(...)` and clears it once consumed.
 */
object NavKeys {
    /** Raw barcode/QR string produced by the scanner. */
    const val SCANNED_CODE = "scanned_code"
}
