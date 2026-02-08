package com.office.restobook

import android.app.Application
import com.google.firebase.FirebaseApp
import com.office.restobook.data.firestore.FirestoreRepository
import com.office.restobook.repository.RestoRepository

class RestoApplication : Application() {
    
    val firestoreRepo by lazy { FirestoreRepository() }
    val repository by lazy { RestoRepository(firestoreRepo) }
    
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
