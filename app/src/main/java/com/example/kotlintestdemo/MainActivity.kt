package com.example.kotlintestdemo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


/**
 * 测试程序：kotlin协程
 *  三种写法：
 *      1. 在UI层使用，MainScope
 *      2. 在MVVM中配合jetpack一起使用，VM、LiveData、Lifecycle等
 *      3. Lifecycle ktx对协程的扩展
 */
class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private var showTextButton: Button? = null
    private var contentTextView: TextView? = null
    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, throwable ->
            LogUtil.log("网络请求发生错误：${throwable.message}")
            contentTextView?.text = "网络请求发生错误，请检查网络连接"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        showTextButton = findViewById<Button>(R.id.btn_show_text)
        contentTextView = findViewById<TextView>(R.id.tv_text)
        showTextButton?.setOnClickListener {
//            loadDataNormal()
            // 使用协程
            LogUtil.log(Thread.currentThread().name)
            launch(exceptionHandler) {
                contentTextView?.text = withContext(Dispatchers.IO) {
                    loadDataByCoroutine()
                }
            }
            LogUtil.log("main thread run over")
        }
    }

    private fun loadDataNormal() {
        // 请求数据并返回，wan-android url: https://wanandroid.com/wxarticle/chapters/json
        Thread {
            val okHttpClient = OkHttpClient()
            val request = Request.Builder()
                    .url("https://wanandroid.com/wxarticle/chapters/json")
                    .get()
                    .build()
            val call = okHttpClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        contentTextView?.text = e.message
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val data = response.body().string()
                    runOnUiThread {
                        contentTextView?.text = data
                    }
                }
            })
        }.start()
    }

private suspend fun loadDataByCoroutine() = suspendCoroutine<String> { con ->
    // 请求数据并返回，wan-android url: https://wanandroid.com/wxarticle/chapters/json
    LogUtil.log("load Data " + Thread.currentThread().name)
    val okHttpClient = OkHttpClient()
    val request = Request.Builder()
            .url("https://wanandroid.com/wxarticle/chapters/json")
            .get()
            .build()
    val call = okHttpClient.newCall(request)
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            con.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            con.resume(response.body().string())
        }
    })
}

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

}