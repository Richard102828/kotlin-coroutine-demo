package com.example.kotlintestdemo.mvvm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.kotlintestdemo.LogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*

/**
 * @author Wenhao Zhang
 * @date on 2021/3/29
 * @describe
 */
class TextViewModel : ViewModel(), CoroutineScope by MainScope() {
    private val netData: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    private val countdownData: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    private val testChannelData: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LogUtil.log("网络请求发生错误：${throwable.message}")
        netData.value = "网络请求失败，请检查网络连接"
    }
    private val channel by lazy {
        Channel<Int>(capacity = Channel.UNLIMITED)
    }

    fun getNetDatas(): LiveData<String> {
        loadData()
        return netData
    }

    @ExperimentalCoroutinesApi
    fun getCountdownData(): LiveData<String> {
        loadDataFlow()
        return countdownData
    }

    @ExperimentalCoroutinesApi
    fun getTestChannelData(): LiveData<Int> {
        launch {
            loadOneToTen()
            repeat(10) {
                delay(500)
                testChannelData.value = channel.receive()
            }
        }
        return testChannelData
    }

    private fun loadData() {
        LogUtil.log("网络请求（马上开始）：" + Thread.currentThread().name)
        launch(exceptionHandler) {
            LogUtil.log("网络请求：" + Thread.currentThread().name)
            netData.value = Repository.loadData()
        }
    }

    @ExperimentalCoroutinesApi
    private fun loadDataFlow() {
        LogUtil.log("倒计时（马上开始）：" + Thread.currentThread().name)
        launch(exceptionHandler) {
            LogUtil.log("倒计时：" + Thread.currentThread().name)
            Repository.loadCountdownDataFlow()
                    .onStart { countdownData.value = "倒计时开始" }
                    .onCompletion {
                        LogUtil.log("数据被吞了？ $it")
                        LogUtil.log("完成时间: ${System.currentTimeMillis()}")
                        countdownData.value = "SHOW"
                    }
                    .map { it -> "$it" }
                    .collect {
                        if (it == "0") LogUtil.log("最后一条数据的时间: ${System.currentTimeMillis()}")
                        countdownData.value = it
                    }
        }
    }

    @ExperimentalCoroutinesApi
    private fun loadOneToTen() {
        // 开两个字协程去请求数据
        launch (Dispatchers.IO) {
            LogUtil.log("1 .. 5加入channel")
            Repository.loadOneToFive(channel)
        }
        launch (Dispatchers.IO) {
            LogUtil.log("6 .. 10加入channel")
            Repository.loadSixToTen(channel)
        }
    }

}