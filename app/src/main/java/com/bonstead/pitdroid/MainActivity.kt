package com.bonstead.pitdroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Fix: Lade till parent-anropet korrekt
        setContentView(R.layout.activity_main)

        // Starta med instrumentpanelen som standardvy
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DashFragment())
            .commit()
    }
}