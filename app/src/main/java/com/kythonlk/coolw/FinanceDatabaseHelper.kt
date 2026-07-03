package com.kythonlk.coolw

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

enum class TransactionType { INCOME, EXPENSE }

data class Transaction(
    val id: Int,
    val type: TransactionType,
    val amount: Double,
    val category: String,
    val note: String,
    val timeInMillis: Long
)

data class CategoryTotal(val category: String, val total: Double)

data class MonthSummary(val income: Double, val expense: Double) {
    val balance: Double get() = income - expense
}

class FinanceDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "finance.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "transactions"
        const val COLUMN_ID = "id"
        const val COLUMN_TYPE = "type"
        const val COLUMN_AMOUNT = "amount"
        const val COLUMN_CATEGORY = "category"
        const val COLUMN_NOTE = "note"
        const val COLUMN_TIME = "time_millis"

        val INCOME_CATEGORIES = listOf("Salary", "Business", "Gift", "Interest", "Other")
        val EXPENSE_CATEGORIES = listOf(
            "Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Other"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_TYPE TEXT, " +
                "$COLUMN_AMOUNT REAL, " +
                "$COLUMN_CATEGORY TEXT, " +
                "$COLUMN_NOTE TEXT, " +
                "$COLUMN_TIME INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addTransaction(
        type: TransactionType,
        amount: Double,
        category: String,
        note: String,
        timeInMillis: Long
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TYPE, type.name)
            put(COLUMN_AMOUNT, amount)
            put(COLUMN_CATEGORY, category)
            put(COLUMN_NOTE, note)
            put(COLUMN_TIME, timeInMillis)
        }
        val id = db.insert(TABLE_NAME, null, values)
        db.close()
        return id
    }

    fun deleteTransaction(id: Int): Int {
        val db = writableDatabase
        val result = db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result
    }

    fun getTransactionsForMonth(year: Int, month: Int): List<Transaction> {
        val (start, end) = monthRange(year, month)
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $COLUMN_TIME >= ? AND $COLUMN_TIME < ? ORDER BY $COLUMN_TIME DESC",
            arrayOf(start.toString(), end.toString())
        )
        val transactions = readTransactions(cursor)
        db.close()
        return transactions
    }

    fun getAllTransactions(): List<Transaction> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIME DESC", null)
        val transactions = readTransactions(cursor)
        db.close()
        return transactions
    }

    private fun readTransactions(cursor: android.database.Cursor): List<Transaction> {
        val transactions = ArrayList<Transaction>()
        if (cursor.moveToFirst()) {
            do {
                transactions.add(
                    Transaction(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        type = TransactionType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE))),
                        amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT)),
                        category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)),
                        note = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE)) ?: "",
                        timeInMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIME))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return transactions
    }

    fun getMonthSummary(year: Int, month: Int): MonthSummary {
        val (start, end) = monthRange(year, month)
        val db = readableDatabase
        var income = 0.0
        var expense = 0.0
        val cursor = db.rawQuery(
            "SELECT $COLUMN_TYPE, SUM($COLUMN_AMOUNT) FROM $TABLE_NAME " +
                "WHERE $COLUMN_TIME >= ? AND $COLUMN_TIME < ? GROUP BY $COLUMN_TYPE",
            arrayOf(start.toString(), end.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                val type = cursor.getString(0)
                val sum = cursor.getDouble(1)
                if (type == TransactionType.INCOME.name) income = sum
                if (type == TransactionType.EXPENSE.name) expense = sum
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return MonthSummary(income, expense)
    }

    fun getExpenseBreakdownForMonth(year: Int, month: Int): List<CategoryTotal> {
        val (start, end) = monthRange(year, month)
        val breakdown = ArrayList<CategoryTotal>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_CATEGORY, SUM($COLUMN_AMOUNT) FROM $TABLE_NAME " +
                "WHERE $COLUMN_TYPE = ? AND $COLUMN_TIME >= ? AND $COLUMN_TIME < ? " +
                "GROUP BY $COLUMN_CATEGORY ORDER BY SUM($COLUMN_AMOUNT) DESC",
            arrayOf(TransactionType.EXPENSE.name, start.toString(), end.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                breakdown.add(CategoryTotal(cursor.getString(0), cursor.getDouble(1)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return breakdown
    }

    private fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return Pair(start.timeInMillis, end.timeInMillis)
    }
}
