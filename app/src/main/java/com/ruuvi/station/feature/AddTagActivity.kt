package com.ruuvi.station.feature

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import com.google.android.material.snackbar.Snackbar
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import com.ruuvi.station.R
import com.ruuvi.station.adapters.AddTagAdapter
import com.ruuvi.station.database.RuuviTagRepository
import com.ruuvi.station.database.tables.RuuviTagEntity
import com.ruuvi.station.util.Starter
import com.ruuvi.station.util.Utils
import kotlinx.android.synthetic.main.activity_add_tag.toolbar
import kotlinx.android.synthetic.main.content_add_tag.no_tags
import kotlinx.android.synthetic.main.content_add_tag.tag_layout
import kotlinx.android.synthetic.main.content_add_tag.tag_listView
import java.util.ArrayList
import java.util.Calendar

class AddTagActivity : AppCompatActivity() {
    private var adapter: AddTagAdapter? = null
    private var tags: MutableList<RuuviTagEntity>? = null
    lateinit var starter: Starter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_tag)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        starter = Starter(this)

        tags = ArrayList()
        adapter = AddTagAdapter(this, tags)
        tag_listView.adapter = adapter

        tag_listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
            val tag = tag_listView.getItemAtPosition(i) as RuuviTagEntity
            if (RuuviTagRepository.get(tag.id)?.favorite == true) {
                Toast.makeText(this, getString(R.string.tag_already_added), Toast.LENGTH_SHORT)
                    .show()
                return@OnItemClickListener
            }
            tag.defaultBackground = getKindaRandomBackground()
            tag.update()
            val settingsIntent = Intent(this, TagSettings::class.java)
            settingsIntent.putExtra(TagSettings.TAG_ID, tag.id)
            startActivityForResult(settingsIntent, 1)
        }

        adapter!!.notifyDataSetChanged()

        val handler = Handler()
        handler.post(object : Runnable {
            override fun run() {
                tags?.clear()
                tags?.addAll(ArrayList(RuuviTagRepository.getAll(false)))
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.SECOND, -5)
                var i = 0
                tags?.let { tags ->
                    while (i < tags.size) {
                        tags[i].updateAt?.time?.let { time ->
                            if (time < calendar.time.time) {
                                tags.removeAt(i)
                                i--
                            }
                        }
                        i++
                    }
                    if (tags.size > 0) {
                        Utils.sortTagsByRssi(tags)
                        no_tags.visibility = View.INVISIBLE
                    } else
                        no_tags.visibility = View.VISIBLE
                    if (adapter != null) adapter?.notifyDataSetChanged()

                }

                handler.postDelayed(this, 1000)
            }
        })

        starter.getThingsStarted()
    }

    override fun onResume() {
        super.onResume()
        checkBluetooth()
    }

    private fun checkBluetooth(): Boolean {
        if (starter.isBluetoothEnabled()) {
            return true
        }
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivity(enableBtIntent)
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            finish()
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            10 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // party
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        starter.requestPermissions()
                    } else {
                        showPermissionSnackbar(this)
                    }
                    Toast.makeText(applicationContext, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPermissionSnackbar(activity: Activity) {
        val snackbar = Snackbar.make(tag_layout, getString(R.string.location_permission_needed), Snackbar.LENGTH_LONG)
        snackbar.setAction(getString(R.string.settings)) {
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            val uri = Uri.fromParts("package", activity.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
        }
        snackbar.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        finish()
    }

    private fun isBackgroundInUse(tags: List<RuuviTagEntity>, background: Int): Boolean {
        for (tag in tags) {
            if (tag.defaultBackground == background) return true
        }
        return false
    }

    private fun getKindaRandomBackground(): Int {
        val tags = RuuviTagRepository.getAll(true)
        var bg = (Math.random() * 9.0).toInt()
        for (i in 0..99) {
            if (!isBackgroundInUse(tags, bg)) {
                return bg
            }
            bg = (Math.random() * 9.0).toInt()
        }
        return bg
    }
}
