package com.example.kotlintestdemo.mvvm

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.kotlintestdemo.R
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * 示例：kotlin Coroutine/flow + mvvm + okhttp
 */
class ShowTextActivity : AppCompatActivity() {
    private val showBtn by lazy {
        findViewById<Button>(R.id.btn_show)
    }
    private val testChannelTV by lazy {
        findViewById<TextView>(R.id.tv_test_channel_text)
    }
    private val netContentTV by lazy {
        findViewById<TextView>(R.id.tv_net_content)
    }
    private var viewModel: TextViewModel? = null

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_text)
        viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)).get(TextViewModel::class.java)
        showBtn.setOnClickListener { btn ->
            viewModel?.getNetDatas()?.observe(this, Observer {
                netContentTV.text = it
            })
            viewModel?.getCountdownData()?.observe(this, Observer {
                // 防止在倒计时结束之前每次点击都会创建协程发送流，造成button text显示出错
                val button = btn as Button
                button.isClickable = false
                button.text = it
                if (it == "SHOW") {
                    button.isClickable = true
                }
            })
            // TODO loadOneToTen
            viewModel?.getTestChannelData()?.observe(this, Observer {
                testChannelTV.text = "$it"
            })
        }
    }
}