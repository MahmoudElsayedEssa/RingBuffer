package com.example.ringbuffer.breadcrumbs.storage.Database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BreadcrumbDbHelper(context: Context) : SQLiteOpenHelper(context, "breadcrumbs.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER
            )
        """
        )

        db?.execSQL(
            """
            CREATE TABLE breadcrumbs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER,
                timestamp INTEGER,
                event TEXT,
                FOREIGN KEY(session_id) REFERENCES sessions(id)
            )
        """
        )
//        db?.execSQL(
//            """
//            CREATE INDEX IF NOT EXISTS idx_breadcrumbs_session_time
//                ON breadcrumbs(session_id, timestamp)
//        """
//        )

    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS breadcrumbs")
        db?.execSQL("DROP TABLE IF EXISTS sessions")
        onCreate(db)
    }


}
