package com.kythonlk.coolw

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TodoItem(
    val id: Int,
    val title: String,
    val description: String,
    val timeInMillis: Long,
    val isCompleted: Boolean,
    val alarmScheduled: Boolean
)

class TodoDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "todos.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "todos"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_TIME = "time_millis"
        const val COLUMN_COMPLETED = "is_completed"
        const val COLUMN_ALARM = "alarm_scheduled"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_TITLE TEXT, " +
                "$COLUMN_DESCRIPTION TEXT, " +
                "$COLUMN_TIME INTEGER, " +
                "$COLUMN_COMPLETED INTEGER, " +
                "$COLUMN_ALARM INTEGER)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addTodo(title: String, description: String, timeInMillis: Long, alarmScheduled: Boolean): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, title)
            put(COLUMN_DESCRIPTION, description)
            put(COLUMN_TIME, timeInMillis)
            put(COLUMN_COMPLETED, 0)
            put(COLUMN_ALARM, if (alarmScheduled) 1 else 0)
        }
        val id = db.insert(TABLE_NAME, null, values)
        db.close()
        return id
    }

    fun getAllTodos(): List<TodoItem> {
        val todoList = ArrayList<TodoItem>()
        val selectQuery = "SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIME ASC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val todo = TodoItem(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION)),
                    timeInMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIME)),
                    isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED)) == 1,
                    alarmScheduled = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ALARM)) == 1
                )
                todoList.add(todo)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return todoList
    }

    fun deleteTodo(id: Int): Int {
        val db = this.writableDatabase
        val result = db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result
    }

    fun updateCompletion(id: Int, isCompleted: Boolean): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_COMPLETED, if (isCompleted) 1 else 0)
        }
        val result = db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result
    }

    fun updateAlarmStatus(id: Int, alarmScheduled: Boolean): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ALARM, if (alarmScheduled) 1 else 0)
        }
        val result = db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result
    }
}
