package com.warehouse.inventory.ui.products

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.DetailRow
import com.warehouse.inventory.ui.common.FieldError
import com.warehouse.inventory.ui.common.LabeledDropdown
import com.warehouse.inventory.ui.common.LoadingState
import com.warehouse.inventory.ui.common.Pill
import com.warehouse.inventory.ui.common.ProductImage
import com.warehouse.inventory.ui.common.ScannableTextField
import com.warehouse.inventory.ui.common.SectionHeader
import com.warehouse.inventory.ui.common.ThinDivider
import com.warehouse.inventory.util.ImageStorage
import com.warehouse.inventory.util.ProductUnitLabels

/**
 * Add / edit product.
 *
 * The form is grouped into identification, stock, pricing and details so a long single
 * column stays navigable. The stock section behaves differently by mode: creating a product
 * offers an opening balance and the warehouse to book it into, while editing shows the
 * current balance read-only with a route to the adjustment screen — quantities never change
 * without a transaction behind them.
 */
@Composable
fun ProductFormScreen(
    productId: Long?,
    onDone: () -> Unit,
    onScanRequest: () -> Unit,
    onAdjustStock: (Long) -> Unit,
    viewModel: ProductFormViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val stockByWarehouse by viewModel.stockByWarehouse.collectAsStateWithLifecycle()
    val showFinancials by viewModel.showFinancials.collectAsStateWithLifecycle()
    val scannedCode by viewModel.scannedCode.collectAsStateWithLifecycle()

    LaunchedEffect(productId) { viewModel.load(productId) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    LaunchedEffect(scannedCode) { scannedCode?.let(viewModel::onScannedCode) }

    val snackbarHost = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // Holds the target path for a capture that is still in flight, so the file can be
    // deleted again if the user backs out of the camera.
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onImageCaptured(pendingCameraPath)
        else ImageStorage.deleteIfExists(pendingCameraPath)
        pendingCameraPath = null
    }

    fun launchCamera() {
        val (uri, file) = ImageStorage.createCaptureTarget(context)
        pendingCameraPath = file.absolutePath
        takePicture.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onImageCaptured(ImageStorage.copyFromUri(context, uri))
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(
                    if (state.isEditing) R.string.edit_product else R.string.add_product
                ),
                onBack = onDone
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ------------------------------------------------------------ photo
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProductImage(
                    imagePath = state.imagePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera()
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.take_photo))
                }
                OutlinedButton(
                    onClick = { pickImage.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.choose_gallery))
                }
            }

            // --------------------------------------------------- identification
            SectionHeader(stringResource(R.string.section_identification))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.product_name)) },
                singleLine = true,
                isError = state.nameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.nameError) FieldError(stringResource(R.string.required_field))

            LabeledDropdown(
                fieldLabel = stringResource(R.string.category),
                selected = categories.firstOrNull { it.id == state.categoryId },
                items = categories,
                label = { it.name },
                onSelected = { category: CategoryEntity -> viewModel.onCategory(category.id) },
                placeholder = stringResource(R.string.select_category)
            )

            OutlinedTextField(
                value = state.sku,
                onValueChange = viewModel::onSku,
                label = { Text(stringResource(R.string.sku)) },
                singleLine = true,
                isError = state.skuError != null,
                supportingText = { Text(stringResource(R.string.optional)) },
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(state.skuError?.let { stringResource(it) })

            ScannableTextField(
                label = stringResource(R.string.barcode),
                value = state.barcode,
                onValueChange = viewModel::onBarcode,
                onScanClick = onScanRequest,
                isError = state.barcodeError != null,
                supportingText = stringResource(R.string.scan_to_fill)
            )
            FieldError(state.barcodeError?.let { stringResource(it) })

            LabeledDropdown(
                fieldLabel = stringResource(R.string.unit),
                selected = state.unit,
                items = ProductUnit.entries,
                label = { stringResource(ProductUnitLabels.stringRes(it)) },
                onSelected = viewModel::onUnit
            )

            // ------------------------------------------------------------ stock
            SectionHeader(stringResource(R.string.section_stock))

            if (state.isEditing) {
                // Read-only: stock only moves through receive / issue / adjust / transfer, so
                // that every balance has a transaction explaining it.
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.current_quantity),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Pill(
                                text = stringResource(
                                    R.string.quantity_with_unit,
                                    state.currentQuantity.toString(),
                                    stringResource(ProductUnitLabels.stringRes(state.unit))
                                ),
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.stock_edit_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (stockByWarehouse.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            ThinDivider()
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.stock_by_warehouse),
                                style = MaterialTheme.typography.labelLarge
                            )
                            stockByWarehouse.forEach { row ->
                                DetailRow(
                                    label = row.warehouseName,
                                    value = row.quantity.toString()
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { state.productId?.let(onAdjustStock) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.menu_adjustment))
                        }
                    }
                }
            } else {
                LabeledDropdown(
                    fieldLabel = stringResource(R.string.initial_warehouse),
                    selected = warehouses.firstOrNull { it.id == state.initialWarehouseId },
                    items = warehouses,
                    label = { it.name },
                    onSelected = { warehouse: WarehouseEntity ->
                        viewModel.onInitialWarehouse(warehouse.id)
                    },
                    secondaryLabel = { it.code }
                )
                OutlinedTextField(
                    value = state.initialQuantity,
                    onValueChange = viewModel::onInitialQuantity,
                    label = { Text(stringResource(R.string.initial_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = state.lowStockThreshold,
                onValueChange = viewModel::onThreshold,
                label = { Text(stringResource(R.string.low_stock_threshold)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // ---------------------------------------------------------- pricing
            // Omitted entirely for viewers rather than disabled: a disabled field still
            // shows the figure.
            if (showFinancials) {
                SectionHeader(stringResource(R.string.section_pricing))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.purchaseCost,
                        onValueChange = viewModel::onPurchaseCost,
                        label = { Text(stringResource(R.string.purchase_cost)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.sellingPrice,
                        onValueChange = viewModel::onSellingPrice,
                        label = { Text(stringResource(R.string.selling_price)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---------------------------------------------------------- details
            SectionHeader(stringResource(R.string.section_details))
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::onLocation,
                label = { Text(stringResource(R.string.warehouse_location)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = { Text(stringResource(R.string.notes)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
