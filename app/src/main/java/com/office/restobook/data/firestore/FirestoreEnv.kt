package com.office.restobook.data.firestore

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.office.restobook.BuildConfig

object FirestoreEnv {

    private val db = FirebaseFirestore.getInstance()

    private fun root() = "env_${BuildConfig.FIREBASE_ENV}"

    private fun dataDoc() =
        db.collection(root()).document("data")

    fun orders(): CollectionReference =
        dataDoc().collection("orders")

    fun menuItems(): CollectionReference =
        dataDoc().collection("menuItems")

    fun orderItems(): CollectionReference =
        dataDoc().collection("orderItems")

    fun bills(): CollectionReference =
        dataDoc().collection("bills")

    fun expenses(): CollectionReference =
        dataDoc().collection("expenses")
}