package com.smoothie

import android.graphics.BitmapFactory
import android.os.Bundle
import android.app.Activity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.androidobservability.R

class SmoothieListActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smoothie_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = SmoothieAdapter(
            smoothies = buildSmoothies(),
            imageLoader = { fileName ->
                assets.open("smoothie/images/$fileName").use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        )
    }

    private fun buildSmoothies(): List<SmoothieItem> {
        return listOf(
            SmoothieItem("Berry Blue", "berry-blue.jpg"),
            SmoothieItem("Carrot Chops", "carrot-chops.jpg"),
            SmoothieItem("Hulking Lemonade", "hulking-lemonade.jpg"),
            SmoothieItem("Kiwi Cutie", "kiwi-cutie.jpg"),
            SmoothieItem("Lemonberry", "lemonberry.jpg"),
            SmoothieItem("Love You Berry Much", "love-you-berry-much.jpg"),
            SmoothieItem("Mango Jambo", "mango-jambo.jpg"),
            SmoothieItem("One in a Melon", "one-in-a-melon.jpg"),
            SmoothieItem("Papa's Papaya", "papas-papaya.jpg"),
            SmoothieItem("Peanut Butter Cup", "peanut-butter-cup.jpg"),
            SmoothieItem("Piña y Coco", "pina-y-coco.jpg"),
            SmoothieItem("Sailor Man", "sailor-man.jpg"),
            SmoothieItem("That's a S'more", "thats-a-smore.jpg"),
            SmoothieItem("That's Berry Bananas", "thats-berry-bananas.jpg"),
            SmoothieItem("Tropical Blue", "tropical-blue.jpg")
        )
    }
}

