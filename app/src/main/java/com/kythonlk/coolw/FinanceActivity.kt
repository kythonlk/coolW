package com.kythonlk.coolw

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kythonlk.coolw.databinding.ActivityFinanceBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FinanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFinanceBinding
    private lateinit var dbHelper: FinanceDatabaseHelper

    private val calendar = Calendar.getInstance()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeExportToUri(uri)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFinanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = FinanceDatabaseHelper(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPrevMonth.setOnClickListener { shiftMonth(-1) }
        binding.btnNextMonth.setOnClickListener { shiftMonth(1) }
        binding.btnAddIncome.setOnClickListener { showAddTransactionDialog(TransactionType.INCOME) }
        binding.btnAddExpense.setOnClickListener { showAddTransactionDialog(TransactionType.EXPENSE) }
        binding.btnExportJson.setOnClickListener {
            val fileName = "coolw_finance_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            exportLauncher.launch(fileName)
        }

        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun shiftMonth(delta: Int) {
        calendar.add(Calendar.MONTH, delta)
        refreshAll()
    }

    private fun refreshAll() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        binding.tvMonthLabel.text = monthFormat.format(calendar.time).uppercase(Locale.getDefault())

        val summary = dbHelper.getMonthSummary(year, month)
        binding.tvBalance.text = formatSigned(summary.balance)
        binding.tvBalance.setTextColor(
            if (summary.balance < 0) ContextCompat.getColor(this, R.color.nothing_red)
            else ContextCompat.getColor(this, R.color.nothing_text_primary)
        )
        binding.tvIncomeTotal.text = "+${formatAmount(summary.income)}"
        binding.tvExpenseTotal.text = "-${formatAmount(summary.expense)}"

        binding.chartCategory.setData(dbHelper.getExpenseBreakdownForMonth(year, month))

        refreshTransactionList(year, month)
    }

    private fun refreshTransactionList(year: Int, month: Int) {
        binding.transactionListContainer.removeAllViews()
        val transactions = dbHelper.getTransactionsForMonth(year, month)

        if (transactions.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No transactions this month."
                setTextColor(Color.parseColor("#55FFFFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }
            binding.transactionListContainer.addView(emptyTv)
            return
        }

        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        for (tx in transactions) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10, 10, 10, 10)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 8)
                layoutParams = params
                background = ContextCompat.getDrawable(this@FinanceActivity, R.drawable.nothing_widget_background)
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(28, 28).apply { marginEnd = 24 }
                setImageResource(if (tx.type == TransactionType.INCOME) R.drawable.ic_income else R.drawable.ic_expense)
                setColorFilter(
                    if (tx.type == TransactionType.INCOME) Color.WHITE
                    else ContextCompat.getColor(this@FinanceActivity, R.color.nothing_red)
                )
            }
            itemLayout.addView(icon)

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 4
                    marginEnd = 10
                }
            }

            val categoryTv = TextView(this).apply {
                text = tx.category.uppercase(Locale.getDefault())
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            textLayout.addView(categoryTv)

            if (tx.note.isNotEmpty()) {
                val noteTv = TextView(this).apply {
                    text = tx.note
                    setTextColor(Color.parseColor("#88FFFFFF"))
                    textSize = 11f
                    setPadding(0, 2, 0, 0)
                }
                textLayout.addView(noteTv)
            }

            val timeTv = TextView(this).apply {
                text = sdf.format(Date(tx.timeInMillis))
                setTextColor(Color.parseColor("#55FFFFFF"))
                textSize = 10f
                setPadding(0, 2, 0, 0)
            }
            textLayout.addView(timeTv)

            itemLayout.addView(textLayout)

            val amountTv = TextView(this).apply {
                text = (if (tx.type == TransactionType.INCOME) "+" else "-") + formatAmount(tx.amount)
                setTextColor(
                    if (tx.type == TransactionType.INCOME) Color.WHITE
                    else ContextCompat.getColor(this@FinanceActivity, R.color.nothing_red)
                )
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            }
            itemLayout.addView(amountTv)

            val deleteBtn = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#55FFFFFF"))
                textSize = 16f
                setPadding(24, 12, 12, 12)
                isClickable = true
                focusable = View.FOCUSABLE
                setOnClickListener {
                    dbHelper.deleteTransaction(tx.id)
                    refreshAll()
                    CoolWPrefs.notifyWidgetsUpdate(this@FinanceActivity)
                    Toast.makeText(this@FinanceActivity, "Transaction deleted", Toast.LENGTH_SHORT).show()
                }
            }
            itemLayout.addView(deleteBtn)

            binding.transactionListContainer.addView(itemLayout)
        }
    }

    private fun showAddTransactionDialog(initialType: TransactionType) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val toggleIncome = dialogView.findViewById<TextView>(R.id.dialog_toggle_income)
        val toggleExpense = dialogView.findViewById<TextView>(R.id.dialog_toggle_expense)
        val etAmount = dialogView.findViewById<android.widget.EditText>(R.id.dialog_et_amount)
        val etNote = dialogView.findViewById<android.widget.EditText>(R.id.dialog_et_note)
        val spinnerCategory = dialogView.findViewById<android.widget.Spinner>(R.id.dialog_spinner_category)
        val tvSelectedTime = dialogView.findViewById<TextView>(R.id.dialog_tv_selected_time)
        val btnSelectTime = dialogView.findViewById<TextView>(R.id.dialog_btn_select_time)

        var selectedType = initialType
        val pickerCalendar = Calendar.getInstance()
        var selectedTimeInMillis = pickerCalendar.timeInMillis

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        tvSelectedTime.text = sdf.format(Date(selectedTimeInMillis))

        fun categoriesFor(type: TransactionType) =
            if (type == TransactionType.INCOME) FinanceDatabaseHelper.INCOME_CATEGORIES
            else FinanceDatabaseHelper.EXPENSE_CATEGORIES

        fun applyToggleStyle() {
            val incomeSelected = selectedType == TransactionType.INCOME
            toggleIncome.setBackgroundColor(Color.parseColor(if (incomeSelected) "#FFFFFF" else "#1AFFFFFF"))
            toggleIncome.setTextColor(Color.parseColor(if (incomeSelected) "#000000" else "#88FFFFFF"))
            toggleExpense.setBackgroundColor(Color.parseColor(if (!incomeSelected) "#FFFF3B30" else "#1AFFFFFF"))
            toggleExpense.setTextColor(Color.parseColor(if (!incomeSelected) "#FFFFFF" else "#88FFFFFF"))

            val adapter = ArrayAdapter(this, R.layout.spinner_item, categoriesFor(selectedType))
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerCategory.adapter = adapter
        }
        applyToggleStyle()

        toggleIncome.setOnClickListener {
            selectedType = TransactionType.INCOME
            applyToggleStyle()
        }
        toggleExpense.setOnClickListener {
            selectedType = TransactionType.EXPENSE
            applyToggleStyle()
        }

        btnSelectTime.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    pickerCalendar.set(Calendar.YEAR, year)
                    pickerCalendar.set(Calendar.MONTH, month)
                    pickerCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    TimePickerDialog(
                        this,
                        { _, hourOfDay, minute ->
                            pickerCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            pickerCalendar.set(Calendar.MINUTE, minute)
                            pickerCalendar.set(Calendar.SECOND, 0)
                            pickerCalendar.set(Calendar.MILLISECOND, 0)

                            selectedTimeInMillis = pickerCalendar.timeInMillis
                            tvSelectedTime.text = sdf.format(Date(selectedTimeInMillis))
                        },
                        pickerCalendar.get(Calendar.HOUR_OF_DAY),
                        pickerCalendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                pickerCalendar.get(Calendar.YEAR),
                pickerCalendar.get(Calendar.MONTH),
                pickerCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Add Transaction")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val category = spinnerCategory.selectedItem as? String ?: "Other"
                val note = etNote.text.toString().trim()

                dbHelper.addTransaction(selectedType, amount, category, note, selectedTimeInMillis)
                refreshAll()
                CoolWPrefs.notifyWidgetsUpdate(this)
                Toast.makeText(this, "Transaction added", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun writeExportToUri(uri: Uri) {
        val transactions = dbHelper.getAllTransactions()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val jsonArray = JSONArray()
        for (tx in transactions) {
            jsonArray.put(
                JSONObject().apply {
                    put("id", tx.id)
                    put("type", tx.type.name)
                    put("amount", tx.amount)
                    put("category", tx.category)
                    put("note", tx.note)
                    put("date", isoFormat.format(Date(tx.timeInMillis)))
                }
            )
        }
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jsonArray.toString(2).toByteArray())
            }
            Toast.makeText(this, "Exported ${transactions.size} transactions", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatAmount(value: Double): String = String.format(Locale.getDefault(), "%,.2f", value)

    private fun formatSigned(value: Double): String {
        val sign = if (value < 0) "-" else ""
        return sign + formatAmount(abs(value))
    }
}
