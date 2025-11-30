package com.example.androidobservability.masking

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.ComponentActivity
import android.app.AlertDialog
import com.example.androidobservability.R

class XMLMaskingActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val launchAsDialog = intent.getBooleanExtra(EXTRA_LAUNCH_AS_DIALOG, false)
		val launchAsConfirmation = intent.getBooleanExtra(EXTRA_LAUNCH_AS_CONFIRMATION, false)
		if (launchAsDialog) {
			if (launchAsConfirmation) {
				setTheme(R.style.Theme_AndroidObservability_ConfirmationDialog)
			} else {
				setTheme(R.style.Theme_AndroidObservability_Dialog)
			}
		}

		setContentView(R.layout.activity_masking_bench)

		val topBar = findViewById<LinearLayout>(R.id.top_bar)
		val closeButton = findViewById<ImageButton>(R.id.button_close)
		// Show top bar only for full-screen dialog variant
		topBar.visibility = if (launchAsDialog && !launchAsConfirmation) android.view.View.VISIBLE else android.view.View.GONE
		closeButton.setOnClickListener { finish() }

		findViewById<Button>(R.id.button_full_screen).setOnClickListener {
			startActivity(
				Intent(this, XMLMaskingActivity::class.java).apply {
					putExtra(EXTRA_LAUNCH_AS_DIALOG, true)
				}
			)
		}
		findViewById<Button>(R.id.button_confirmation).setOnClickListener {
			startActivity(
				Intent(this, XMLMaskingActivity::class.java).apply {
					putExtra(EXTRA_LAUNCH_AS_DIALOG, true)
					putExtra(EXTRA_LAUNCH_AS_CONFIRMATION, true)
				}
			)
		}
		findViewById<Button>(R.id.button_alert).setOnClickListener {
			AlertDialog.Builder(this)
				.setTitle("Alert")
				.setMessage("This is an example alert dialog.")
				.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
				.show()
		}
		findViewById<Button>(R.id.button_toast).setOnClickListener {
			Toast.makeText(this, "This is an example toast.", Toast.LENGTH_SHORT).show()
		}
		findViewById<Button>(R.id.button_floating_popup).setOnClickListener {
			val messageView = TextView(this).apply {
				text = "This is a floating popup."
				setTextColor(Color.BLACK)
				setPadding(48, 32, 48, 32)
			}
			val container = LinearLayout(this).apply {
				setBackgroundColor(Color.WHITE)
				elevation = 8f
				addView(messageView)
			}
			val popup = PopupWindow(
				container,
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT,
				true
			).apply {
				isOutsideTouchable = true
				setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			}
			val root = findViewById<View>(android.R.id.content)
			popup.showAtLocation(root, Gravity.CENTER, 0, 0)
		}

		val firstField = findViewById<EditText>(R.id.input_first)
		val secondField = findViewById<EditText>(R.id.input_second)
		// Fields are present for masking demonstration; no extra behavior needed here.
	}

	companion object {
		const val EXTRA_LAUNCH_AS_DIALOG = "xml_masking_launch_as_dialog"
		const val EXTRA_LAUNCH_AS_CONFIRMATION = "xml_masking_launch_as_confirmation"
	}
}


