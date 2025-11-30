package com.example.androidobservability.masking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
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
			val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

			val params = WindowManager.LayoutParams().apply {
				width = WindowManager.LayoutParams.MATCH_PARENT
				height = WindowManager.LayoutParams.MATCH_PARENT
				format = PixelFormat.TRANSLUCENT
				flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
				type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
				token = window.decorView.applicationWindowToken
				gravity = Gravity.CENTER
			}

			val popupView = FloatingPopupView(this).apply {
				onSendClicked = {
					Toast.makeText(this@XMLMaskingActivity, "Send clicked", Toast.LENGTH_SHORT).show()
				}
				onDismissRequested = {
					try {
						windowManager.removeView(this)
					} catch (_: Exception) {
					}
				}
			}

			windowManager.addView(popupView, params)
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

class FloatingPopupView(context: Context) : FrameLayout(context) {

	var onSendClicked: (() -> Unit)? = null
	var onDismissRequested: (() -> Unit)? = null

	init {
		setBackgroundColor(Color.parseColor("#80000000"))
		isClickable = true
		isFocusable = true

		val content = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			setBackgroundColor(Color.WHITE)
			elevation = 12f
			setPadding(48, 32, 32, 32)
		}

		val label = TextView(context).apply {
			text = "UserName"
			setTextColor(Color.BLACK)
		}

		val sendButton = ImageButton(context).apply {
			setImageResource(android.R.drawable.ic_menu_send)
			contentDescription = "Send"
			background = null
		}

		content.addView(label)
		content.addView(
			sendButton,
			LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			).apply { leftMargin = 24 }
		)

		addView(
			content,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT,
				Gravity.CENTER
			)
		)

		// Outside tap dismiss
		setOnClickListener { onDismissRequested?.invoke() }
		// Consume inner content clicks
		content.setOnClickListener { }
		sendButton.setOnClickListener {
			onSendClicked?.invoke()
			onDismissRequested?.invoke()
		}
	}
}


