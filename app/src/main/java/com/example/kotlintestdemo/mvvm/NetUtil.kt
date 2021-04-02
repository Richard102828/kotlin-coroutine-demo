package com.example.kotlintestdemo.mvvm

import com.example.kotlintestdemo.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * @author Wenhao Zhang
 * @date on 2021/3/29
 * @describe
 */
object NetUtil {

    suspend fun loadData() = withContext(Dispatchers.IO) {
        LogUtil.log("网络请求：" + Thread.currentThread().name)
        doRequest()
    }

    @ExperimentalCoroutinesApi
    suspend fun loadCountdownData() = withContext(Dispatchers.IO) {
        LogUtil.log("倒计时：" + Thread.currentThread().name)
        requestCountdownData()
    }

    /**
     * 只是测试，没有使用泛型
     */
    suspend fun loadOneToFive(sendChannel: SendChannel<Int>) {
        // 模拟网络请求，发送1~5的数字
        for (i in 1..5) {
            sendChannel.send(i)
        }
    }

    suspend fun loadSixToTen(sendChannel: SendChannel<Int>) {
        // 模拟网络请求，发送6~10的数字
        for (i in 6..10) {
            sendChannel.send(i)
        }
    }

    private suspend fun doRequest() = suspendCoroutine<String> { con ->
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://wanandroid.com/wxarticle/chapters/json")
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                con.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                con.resume(response.body().string())
            }
        })
    }

    @ExperimentalCoroutinesApi
    private suspend fun requestCountdownData(): Flow<Int> = flow<Int> {
        for (i in 5 downTo 0) {
            delay(1000)
            emit(i)
        }
    }.flowOn(Dispatchers.IO)
}