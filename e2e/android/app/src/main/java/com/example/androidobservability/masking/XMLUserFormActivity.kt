package com.example.androidobservability.masking

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.androidobservability.R

class XMLUserFormActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_form)

        val inputCardholderName = findViewById<EditText>(R.id.input_cardholder_name)
        val inputCardNumber = findViewById<EditText>(R.id.input_card_number)
        val inputExpiry = findViewById<EditText>(R.id.input_expiry)
        val inputCvv = findViewById<EditText>(R.id.input_cvv)
        val inputZip = findViewById<EditText>(R.id.input_zip_code)
        val buttonSave = findViewById<Button>(R.id.button_save_card)

        buttonSave.setOnClickListener {
            val name = inputCardholderName.text?.toString()?.trim().orEmpty()
            val number = inputCardNumber.text?.toString()?.replace(" ", "")?.trim().orEmpty()
            val expiry = inputExpiry.text?.toString()?.trim().orEmpty()
            val cvv = inputCvv.text?.toString()?.trim().orEmpty()
            val zip = inputZip.text?.toString()?.trim().orEmpty()

            var isValid = true

            if (name.isEmpty()) {
                inputCardholderName.error = "Cardholder name is required"
                isValid = false
            } else {
                inputCardholderName.error = null
            }

            if (number.length !in 13..19 || number.any { !it.isDigit() }) {
                inputCardNumber.error = "Enter a valid card number"
                isValid = false
            } else {
                inputCardNumber.error = null
            }

            val expiryRegex = Regex("""^(0[1-9]|1[0-2])/\d{2}$""")
            if (!expiryRegex.matches(expiry)) {
                inputExpiry.error = "Use MM/YY"
                isValid = false
            } else {
                inputExpiry.error = null
            }

            if (cvv.length !in 3..4 || cvv.any { !it.isDigit() }) {
                inputCvv.error = "Enter a valid CVV"
                isValid = false
            } else {
                inputCvv.error = null
            }

            if (zip.isEmpty() || zip.length < 3 || zip.length > 10) {
                inputZip.error = "Enter a valid ZIP/Postal code"
                isValid = false
            } else {
                inputZip.error = null
            }

            if (isValid) {
                Toast.makeText(this, "Card saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}


