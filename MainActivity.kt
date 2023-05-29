package com.example.attuneilmo

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.starmicronics.stario.StarIOPort
import com.starmicronics.stario.StarIOPortException
import com.starmicronics.starioextension.ICommandBuilder
import com.starmicronics.starioextension.StarIoExt

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).run {
            // Piilottaa navigation barit
            hideSystemUI()
        }

        val webView: WebView = findViewById(R.id.webview)
        var message = ""
        webView.settings.javaScriptEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(false)
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(WebAppInterface(this, webView, message), "Android")
        webView.loadUrl("https://ilmo.attune.fi")

    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    /** Instantiate the interface and set the context */
    class WebAppInterface(private val mContext: Context, private val webView: WebView, private var message: String) {
        @JavascriptInterface
        fun connectPrinter(
            id: Int,
            time: String,
            description: String,
            location: String,
            guide: String
        ) {
            var port: StarIOPort? = null

            try {
                // Avaa portti USBin kautta
                port = StarIOPort.getPort("USB:", "", 10000, mContext)

                //Luo builderin tulostimelle
                val builder = StarIoExt.createCommandBuilder(StarIoExt.Emulation.StarLine)

                checkPrinterStatus(port)

                //Word wrap theorycraft -----------------------------------------------------------
                val words = guide.split(" ") //Ohjeet pilkotaan välilyönnin kohdalla
                var line = ""
                var wrappedText = ""
                for (word in words) {
                    if (line.length + word.length > 23) { //23 = merkkien määrä rivillä
                        wrappedText += "$line\n"
                        line = ""
                    }
                    line += "$word "
                }
                wrappedText += line.trim()
                //Word wrap loppuu ----------------------------------------------------------------

                val dateAndTime = time.replace(", klo", "\nklo") //Erotetaan päivä ja aika rivin vaihdolla
                val locations = location.replace(", ", "\n")  //Rivittää mahdolliset lokaatiot

                builder.beginDocument()

                builder.appendAlignment(ICommandBuilder.AlignmentPosition.Center) //Teksti keskelle
                builder.appendMultiple("Ilmoittautuminen\nvastaanotolle\n".toByteArray(), 2, 2)
                builder.append("\n----------------------------------------\n".toByteArray())
                builder.append("Käynti:\n".toByteArray())
                builder.appendMultiple("$dateAndTime\n".toByteArray(), 2, 2) //Aika
                builder.append("----------------------------------------\n".toByteArray())
                builder.appendMultiple("-Sairaalan nimi-\n".toByteArray(), 3, 3) //Sairaalan nimi
                builder.appendMultiple("$description\n".toByteArray(), 2, 2) //Mitä varten täällä ollaan?
                builder.appendMultiple("$locations\n".toByteArray(), 3, 3) //Huone/kerros/mikä lie
                builder.appendMultiple("\n$wrappedText\n".toByteArray(), 2, 2) //Miten ylempään osoitteeseen pääsee
                builder.append("----------------------------------------\n\n".toByteArray())
                builder.appendMultiple("Vuoronumero:\n\n".toByteArray(), 1, 1)
                builder.appendMultiple("$id".toByteArray(), 6, 6) //ISO vuoronumero
                builder.append("\n".toByteArray())
                builder.append("Teidät kutsutaan sisään tällä numerolla.".toByteArray())


                builder.appendCutPaper(ICommandBuilder.CutPaperAction.PartialCutWithFeed)

                builder.endDocument()

                val command = builder.commands

                // Lähetä tulostimelle
                port.writePort(command, 0, command.size)

                checkPrinterStatus(port)

                // Port close
                StarIOPort.releasePort(port)

            } catch (e: StarIOPortException) {
                // Error
                Log.d("Connecting", "${e.message}")
                message = "Tulostimeen ei saada yhteyttä."
                printerError(port, message)
            } finally {
                try {
                    // Port close
                    StarIOPort.releasePort(port)
                } catch (e: StarIOPortException) {
                    Log.d("Connecting", "${e.message}")
                    message = "Tulostimeen ei saada yhteyttä."
                    printerError(port, message)
                }
            }
        }

        private fun checkPrinterStatus(port: StarIOPort) {
            var status = port.beginCheckedBlock()
            var message = ""
            if (status.coverOpen) {
                message = "Tulostimen kans on auki."
                printerError(port, message)
            } else if (status.receiptPaperEmpty) {
                message = "Kuittipaperi on loppu."
                printerError(port, message)
            } else if (status.offline) {
                message = "Tulostimeen ei saada yhteyttä."
                printerError(port, message)
            }
            status = port.endCheckedBlock()
        }

        private fun printerError(port: StarIOPort?, message: String) {
            //throw StarIOPortException(message);
            webView.post { webView.evaluateJavascript("attunePrinterError('$message');") { result ->
                //TODO: Alertin otsikko?
            } }
            //Port close
            StarIOPort.releasePort(port)
        }
    }
}
