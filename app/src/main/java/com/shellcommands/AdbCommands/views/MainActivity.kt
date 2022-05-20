package com.shellcommands.AdbCommands.views

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethod
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.shellcommands.AdbCommands.BuildConfig
import com.shellcommands.AdbCommands.R
import com.shellcommands.AdbCommands.viewmodels.MainActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    /* View Model */
    private val viewModel: MainActivityViewModel by viewModels()

    /* UI components */
    private lateinit var command: TextInputEditText
    private lateinit var output: MaterialTextView
    private lateinit var outputScrollView: ScrollView
    private lateinit var progress: ProgressBar
    private lateinit var dropDownMenuLayout: TextInputLayout
    private lateinit var commandContainer: TextInputLayout
    private lateinit var resetBtn: Button

    /* Alert dialogs */
    private lateinit var pairDialog: MaterialAlertDialogBuilder
    private lateinit var badAbiDialog: MaterialAlertDialogBuilder

    /* Held when pairing */
    private var pairingLatch = CountDownLatch(0)

    private var lastCommand = ""

    var bookmarkGetResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val text =
                it.data?.getStringExtra(Intent.EXTRA_TEXT) ?: return@registerForActivityResult
            command.setText(text)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        command = findViewById(R.id.command)
        output = findViewById(R.id.output)
        outputScrollView = findViewById(R.id.output_scrollview)
        progress = findViewById(R.id.progress)
        commandContainer = findViewById(R.id.command_container)
        resetBtn = findViewById(R.id.resetBtn)
        dropDownMenuLayout = findViewById(R.id.dropDownMenuLayout)


        val commands = resources.getStringArray(R.array.command_list)
        val arrayAdapter = ArrayAdapter(this, R.layout.dropdown, commands)
        val cmdTextView = findViewById<AutoCompleteTextView>(R.id.cmdTextView)
        cmdTextView.setAdapter(arrayAdapter)
        cmdTextView.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                when (position) {
                    0 -> {
                        viewModel.adb.startLogs()
                        output.append("Launching Logcat Command.\n")
                    }
                    1 -> {
                        viewModel.adb.sendToShellProcess("svc bluetooth enable")
                        output.append("Enabling BlueTooth.\n")
                    }

                    2 -> {
                        viewModel.adb.sendToShellProcess("svc bluetooth disable")
                        output.append("Disabling BlueTooth.\n")
                    }
                    3 -> {
                        viewModel.adb.sendToShellProcess("svc data enable")
                        output.append("Enabling Mobile Data.\n")
                    }
                    4 -> {
                        viewModel.adb.sendToShellProcess("svc data disable")
                        output.append("Disabling Mobile Data.\n")
                    }
                    5 -> {
                        viewModel.adb.sendToShellProcess("am start -a android.settings.AIRPLANE_MODE_SETTINGS && input keyevent 19 && input keyevent 23 ")
                        output.append("Enabling Airplane Mode.\n")
                    }
                    6 -> {
                        viewModel.adb.sendToShellProcess("am start -a android.settings.AIRPLANE_MODE_SETTINGS && input keyevent 19 && input keyevent 23  ")
                        output.append("Disabling Airplane Mode.\n")
                    }
                    7 -> {
                        viewModel.adb.sendToShellProcess("svc power shutdown")
                        output.append("Shutting Down.\n")
                    }
                    8 -> {
                        viewModel.adb.sendToShellProcess("svc power reboot")
                        output.append("Rebooting Device.\n")
                    }
                    9 -> {
                        if (!(commandContainer.visibility == View.VISIBLE)) {
                            commandContainer.visibility = View.VISIBLE
                            output.append("Typing Manual Command.\n")
                            command.requestFocus()
                        } else {
                            commandContainer.visibility = View.GONE
                        }

                    }
                }
            }

        pairDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_title)
            .setCancelable(false)
            .setView(R.layout.dialog_pair)

        badAbiDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bad_abi_title)
            .setMessage(R.string.bad_abi_message)
            .setPositiveButton(R.string.dismiss, null)

        /* Send commands to the ADB instance */
        command.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val text = command.text.toString()
                    lastCommand = text
                    command.text = null
                    lifecycleScope.launch(Dispatchers.IO) {
                        viewModel.adb.sendToShellProcess(text)
                    }
                }
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        resetBtn.setOnClickListener { v ->
            this.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    /* Unpair server and client */
                    with(PreferenceManager.getDefaultSharedPreferences(it).edit()) {
                        putBoolean(getString(R.string.paired_key), false)
                        commit()
                    }

                    viewModel.adb.reset()
                }
                this.finish()
            }
        }

        /* Update the output text */
        viewModel.outputText.observe(this, Observer {
            output.text = it
            outputScrollView.post {
                outputScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                if (commandContainer.visibility == View.VISIBLE){
                    command.requestFocus()
                }
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(command, InputMethod.SHOW_EXPLICIT)
            }
        })

        /* Restart the activity on reset */
        viewModel.adb.closed.observe(this, Observer {
            if (it == true) {
                val intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                finishAffinity()
                startActivity(intent)
                exitProcess(0)
            }
        })

        /* Prepare progress bar, pairing latch, and script executing */
        viewModel.adb.ready.observe(this, Observer {
            if (it != true) {
                runOnUiThread {
                    command.isEnabled = false
                    progress.visibility = View.VISIBLE
                    dropDownMenuLayout.visibility = View.GONE
                    resetBtn.visibility = View.VISIBLE
                }
                return@Observer
            }

            lifecycleScope.launch(Dispatchers.IO) {
                pairingLatch.await()

                runOnUiThread {
                    command.isEnabled = true
                    progress.visibility = View.INVISIBLE
                    commandContainer.visibility = View.GONE
                    resetBtn.visibility = View.GONE
                    dropDownMenuLayout.visibility = View.VISIBLE
                }

                if (viewModel.getScriptFromIntent(intent) != null)
                    executeFromScript()
            }
        })

        /* Check if we need to pair with the device on Android 11 */
        with(PreferenceManager.getDefaultSharedPreferences(this)) {
            if (viewModel.shouldWePair(this)) {
                pairingLatch = CountDownLatch(1)
                viewModel.adb.debug("Requesting pairing information")
                askToPair {
                    with(edit()) {
                        putBoolean(getString(R.string.paired_key), true)
                        apply()
                    }
                    pairingLatch.countDown()
                }
            }
        }

        viewModel.abiUnsupportedDialog(badAbiDialog)
        //  viewModel.piracyCheck(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        exitProcess(0)
    }

    /**
     * Execute a script from the main intent
     */
    private fun executeFromScript() {
        val code = viewModel.getScriptFromIntent(intent) ?: return

        /* Invalidate intent */
        intent.type = ""

        Snackbar.make(output, getString(R.string.snackbar_file_opened), Snackbar.LENGTH_SHORT)
            .setAction(getString(R.string.dismiss)) {}
            .show()

        viewModel.adb.sendScript(code)
    }

    /**
     * Ask the user to pair
     */
    private fun askToPair(callback: Runnable? = null) {
        pairDialog
            .create()
            .apply {
                setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay)) { _, _ ->
                    val port = findViewById<TextInputEditText>(R.id.port)!!.text.toString()
                    val code = findViewById<TextInputEditText>(R.id.code)!!.text.toString()

                    lifecycleScope.launch(Dispatchers.IO) {
                        viewModel.adb.debug("Requesting additional pairing information")
                        viewModel.adb.pair(port, code)

                        callback?.run()
                    }
                }
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.bookmarks -> {
                val intent = Intent(this, BookmarksActivity::class.java)
                    .putExtra(Intent.EXTRA_TEXT, command.text.toString())
                bookmarkGetResult.launch(intent)
                true
            }
            R.id.last_command -> {
                command.setText(lastCommand)
                command.setSelection(lastCommand.length)
                true
            }
            R.id.help -> {
                val intent = Intent(this, HelpActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.share -> {
                try {
                    val uri = FileProvider.getUriForFile(
                        this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        viewModel.adb.outputBufferFile
                    )
                    val intent = Intent(Intent.ACTION_SEND)
                    with(intent) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "file/*"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Snackbar.make(
                        output,
                        getString(R.string.snackbar_intent_failed),
                        Snackbar.LENGTH_SHORT
                    )
                        .setAction(getString(R.string.dismiss)) {}
                        .show()
                }
                true
            }
            R.id.clear -> {
                viewModel.clearOutputText()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }
}