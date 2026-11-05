
package com.example.estacionmeteorologica2

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class EstacionApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseDatabase.getInstance()
            .setPersistenceEnabled(true)
    }
}
