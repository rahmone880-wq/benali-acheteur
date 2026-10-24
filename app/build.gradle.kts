package com.acheteurpro.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

data class OrderItem(
    val description: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double
)

data class InspectionItem(
    val id: String,
    val description: String,
    val orderedQty: Double,
    val unit: String,
    var receivedQty: String = "",
    var rejectedQty: String = "0"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                var currentTab by remember { mutableStateOf("dashboard") }

                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = ComposeColor.White) {
                            NavigationBarItem(
                                selected = currentTab == "dashboard",
                                onClick = {
                                    currentTab = "dashboard"
                                    navController.navigate("dashboard")
                                },
                                label = { Text("Budget") },
                                icon = { Text("📊") }
                            )
                            NavigationBarItem(
                                selected = currentTab == "receipt",
                                onClick = {
                                    currentTab = "receipt"
                                    navController.navigate("receipt")
                                },
                                label = { Text("Réception") },
                                icon = { Text("📦") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            BudgetDashboardView()
                        }
                        composable("receipt") {
                            MaterialReceiptView(onGeneratePdf = { blNumber ->
                                val pdfFile = generatePurchaseOrderPdf(this@MainActivity)
                                Toast.makeText(
                                    this@MainActivity,
                                    "PV enregistré (BL: $blNumber) - PDF : ${pdfFile.name}",
                                    Toast.LENGTH_LONG
                                ).show()
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetDashboardView() {
    val symbols = DecimalFormatSymbols(Locale.FRENCH).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    val formatter = DecimalFormat("#,##0.00 DZD", symbols)

    val allocated = 150_000_000.00
    val committedDa = 12_500_000.00
    val committedPo = 34_800_000.00
    val receivedUnbilled = 8_200_000.00
    val invoiced = 45_000_000.00
    val available = allocated - (committedDa + committedPo + receivedUnbilled + invoiced)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFFF1F5F9))
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Projet : [PRJ-2026-OIL] Base Hassi Messaoud",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ComposeColor(0xFF0F172A)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ComposeColor(0xFFE2E8F0))

                BudgetRow("Budget Alloué", formatter.format(allocated), ComposeColor(0xFF0F172A), true)
                BudgetRow("DA Approuvées", formatter.format(committedDa), ComposeColor(0xFFD97706), false)
                BudgetRow("BC Engagés", formatter.format(committedPo), ComposeColor(0xFF2563EB), false)
                BudgetRow("Réceptions (Non Facturées)", formatter.format(receivedUnbilled), ComposeColor(0xFF7C3AED), false)
                BudgetRow("Factures Rapprochées", formatter.format(invoiced), ComposeColor(0xFF059669), false)

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ComposeColor(0xFFE2E8F0))
                BudgetRow("Budget Disponible Réel", formatter.format(available), ComposeColor(0xFF16A34A), true)

                Spacer(modifier = Modifier.height(12.dp))
                val ratio = ((allocated - available) / allocated).toFloat().coerceIn(0f, 1f)
                
                // تم تعديلها لتأخذ قيمة Float مباشرة بدون أقواس تعطل البناء
                LinearProgressIndicator(
                    progress = ratio,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (ratio > 0.9f) ComposeColor(0xFFDC2626) else ComposeColor(0xFF0284C7),
                    trackColor = ComposeColor(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
fun BudgetRow(label: String, value: String, color: ComposeColor, isBold: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = ComposeColor(0xFF475569))
        Text(value, fontSize = 13.sp, color = color, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialReceiptView(onGeneratePdf: (String) -> Unit) {
    var blNumber by remember { mutableStateOf("") }
    val items = remember {
        mutableStateListOf(
            InspectionItem("1", "Tube Acier Sans Soudure 4\" Sch 40 API 5L", 150.0, "Mètres"),
            InspectionItem("2", "Vanne à Boisseau Sphérique 4\" Cl 300", 12.0, "Unités")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réception Matériel : BC-2026-0042", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ComposeColor(0xFF1E293B),
                    titleContentColor = ComposeColor.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ComposeColor(0xFFF8FAFC))
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ComposeColor.White)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Fournisseur : SARL COMPTOIR INDUSTRIEL DU SUD", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = blNumber,
                        onValueChange = { blNumber = it },
                        label = { Text("N° Bon de Livraison (BL Transport)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Articles à Inspecter", fontWeight = FontWeight.Bold)

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(items) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = ComposeColor.White)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(item.description, fontWeight = FontWeight.Medium)
                            Text("Commandé : ${item.orderedQty} ${item.unit}", fontSize = 12.sp, color = ComposeColor.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = item.receivedQty,
                                    onValueChange = { input -> items[index] = item.copy(receivedQty = input) },
                                    label = { Text("Quantité Reçue") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = item.rejectedQty,
                                    onValueChange = { input -> items[index] = item.copy(rejectedQty = input) },
                                    label = { Text("Rebut") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onGeneratePdf(blNumber) },
                enabled = blNumber.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF0284C7))
            ) {
                Text("Valider la Réception & Générer le PDF", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun generatePurchaseOrderPdf(context: Context): File {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas: Canvas = page.canvas
    val paint = Paint()

    paint.color = Color.BLACK
    paint.textSize = 14f
    paint.isFakeBoldText = true
    canvas.drawText("SPA ALGERIA INDUSTRIAL SOLUTIONS", 40f, 50f, paint)

    paint.textSize = 9f
    paint.isFakeBoldText = false
    canvas.drawText("ACHETEUR PRO - Système d'Approvisionnement", 40f, 65f, paint)

    paint.textSize = 14f
    paint.isFakeBoldText = true
    canvas.drawText("BON DE COMMANDE : BC-2026-0042", 300f, 50f, paint)

    paint.textSize = 9f
    paint.isFakeBoldText = false
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
    canvas.drawText("Date : ${sdf.format(Date())}", 300f, 65f, paint)
    canvas.drawText("Fournisseur : SARL COMPTOIR DU SUD", 300f, 80f, paint)
    canvas.drawText("NIF : 000830012987654 | AI : 30019876543", 300f, 95f, paint)

    var y = 140f
    paint.color = Color.LTGRAY
    paint.style = Paint.Style.FILL
    canvas.drawRect(40f, y - 12f, 555f, y + 5f, paint)

    paint.color = Color.BLACK
    paint.isFakeBoldText = true
    canvas.drawText("Désignation", 45f, y, paint)
    canvas.drawText("Qté", 320f, y, paint)
    canvas.drawText("P.U HT", 400f, y, paint)
    canvas.drawText("Total HT", 480f, y, paint)

    paint.isFakeBoldText = false
    val items = listOf(
        OrderItem("Tube Acier 4\" API 5L", 150.0, "M", 15000.0),
        OrderItem("Vanne à boisseau 4\"", 12.0, "U", 50000.0)
    )

    var totalHt = 0.0
    for (item in items) {
        y += 20f
        val lineTotal = item.quantity * item.unitPrice
        totalHt += lineTotal
        canvas.drawText(item.description, 45f, y, paint)
        canvas.drawText("${item.quantity} ${item.unit}", 320f, y, paint)
        canvas.drawText(String.format(Locale.US, "%.0f", item.unitPrice), 400f, y, paint)
        canvas.drawText(String.format(Locale.US, "%.0f", lineTotal), 480f, y, paint)
    }

    y += 30f
    paint.isFakeBoldText = true
    val tva = totalHt * 0.19
    val ttc = totalHt + tva
    canvas.drawText("Total Général HT : ${String.format(Locale.US, "%.2f DZD", totalHt)}", 320f, y, paint)
    canvas.drawText("TVA (19%) : ${String.format(Locale.US, "%.2f DZD", tva)}", 320f, y + 15f, paint)
    canvas.drawText("Montant Net TTC : ${String.format(Locale.US, "%.2f DZD", ttc)}", 320f, y + 30f, paint)

    pdfDocument.finishPage(page)

    val outputFile = File(context.cacheDir, "BC-2026-0042.pdf")
    val fos = FileOutputStream(outputFile)
    pdfDocument.writeTo(fos)
    pdfDocument.close()
    fos.close()

    return outputFile
}
