package com.example.kotlintestdemo.mvvm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel

/**
 * @author Wenhao Zhang
 * @date on 2021/3/29
 * @describe
 */
object Repository {
    suspend fun loadData() = NetUtil.loadData()

    @ExperimentalCoroutinesApi
    suspend fun loadCountdownDataFlow() = NetUtil.loadCountdownData()

    // TODO 这里，我理解：参数类型写死才是最好的，因为上层对数据并不知情，应该由下层来控制
    suspend fun loadOneToFive(channel: SendChannel<Int>) = NetUtil.loadOneToFive(channel)

    // TODO 这里，我理解：参数类型写死才是最好的，因为上层对数据并不知情，应该由下层来控制
    suspend fun loadSixToTen(channel: SendChannel<Int>) = NetUtil.loadSixToTen(channel)
}