# kotlin-coroutine-demo

* 下方对kotlin协程的原理进行的分析
  * 感觉有点长，还有点杂，后面有时间再整理一下

## demo说明

kotlin协程demo，kotlin coroutine + mvvm + okhttp

* 界面如下，点击按钮开始进行网络请求，同时，改变button状态，为倒计时效果，button下方的数据也开始递增

![Screenshot_2021-04-02-14-09-22-796_com.example.ko](/Users/bytedance/AndroidProjects/KotlinTestDemo/kotlin-coroutine-demo/Screenshot_2021-04-02-14-09-22-796_com.example.ko.jpg)

![Screenshot_2021-04-02-14-09-30-279_com.example.ko](/Users/bytedance/AndroidProjects/KotlinTestDemo/kotlin-coroutine-demo/Screenshot_2021-04-02-14-09-30-279_com.example.ko.jpg)

---

# Kotlin协程

* [参考官网](https://docs.gradle.org/current/userguide/what_is_gradle.html)，后期如果api有变动也参考官网
* **这里只对结构性的东西进行记录**
  * 额外话题：直接都没有用过debug打断点来跟源码，现在用了，除了走错之后需要重来之外，真香

## 基础

* 协程（框架）其实就是一套由kotlin官方提供的线程API，一个线程框架

  * 协程并不是一个轻量级的线程，而是很像一个轻量级的线程
  * 现在写完这篇文章之后，我理解：协程（本身）就是一个lambda参数里的逻辑，一段可以挂起和恢复执行的运算逻辑（ps：本文中很多地方使用lambda参数来表示协程）

* 优点：方便，能在同一个代码块里进行多次线程切换，代替了多次的回调，使用看似同步的代码实现了异步的效果

  * 在性能上，使用自旋代替了线程阻塞

  ```
  // 使用下面的异常捕获器与网络请求函数
  launch(exceptionHandler) {
      contentTextView?.text = withContext(Dispatchers.IO) {
          loadDataByCoroutine()
      }
  }
  
  // 定义异常捕获器
  private val exceptionHandler by lazy {
      CoroutineExceptionHandler { _, throwable ->
          LogUtil.log("网络请求发生错误：${throwable.message}")
          contentTextView?.text = "网络请求发生错误，请检查网络连接"
      }
  }
  
  // 使用协程进行网络请求
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
              con.resumeWithException(e)	// 恢复协程，并带有一个异常
          }
  
          override fun onResponse(call: Call, response: Response) {
              con.resume(response.body().string())	// 恢复协程，并带有一个结果
          }
      })
  }
  ```

* 非阻塞式挂起，非阻塞指的是不阻塞线程，挂起是只挂起协程，其实多线程也是非阻塞式的

* 应用场景：解决回调地狱问题、预防ANR问题、自动切换线程、并发不会阻塞线程（用户态 -> 内核态的切换，性能消耗比较大）

## 协程的构建

* Gradle引入协程依赖

  ```
  implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0-M2'	//kotlin协程核心库
  implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.0'	//android需要添加
  ```

* 我了解到6种创建方式（两种全局：`GlobalScope.launch {...}`、`GlobalScope.async {...}`）

  * 下面的构建函数都是`CoroutineScope`的扩展函数，继承了当前CoroutineScope的CoroutineContext和取消操作
  * 可以在context位置使用`CoroutineName("test coroutine 1")`指定名字
    * 打印协程名字，需要设置JVM启动参数：-Dkotlinx.coroutines.debug
      * 这样在代码跑起来的时候，在打印Thread的name的时候，就会自动带上协程的名字
    * 当然，也通可以通过`coroutineContext[CoroutineName]`拿到这个协程的名字，直接输出

* 协程构建器分为两类：自动传播异常的（launch、actor）、向用户暴露异常的（async、produce）。这里不作介绍，具体在异常那块有讲解

* **launch函数**

  * 启动一个协程，不会阻塞当前线程。返回一个 `Job`，可以用于协程的取消等操作
    - 在 `GlobalScope`类与 `CoroutineScope` 类中都存在 `launch` 函数。前者为全局范围的，且不存在父协程，并且除非执行完，或者手动取消，该协程都将一直运行，所以建议少用。后者范围是创建它的父协程
    - `laucnch` 函数有三个参数：`CoroutineContext`、`CoroutineStart`、`block Lambda`

  ```
  //GlobalScope的用法也是类似的，不过不能指定coroutineContext
  CoroutineScope(Dispatchers.Default).launch {
  	Log.d("MainActivity", "new Coroutines")
  }
  Log.d("MainActivity", "Main Thread")
  ```

* **actor函数**

  * 创建一个协程，返回一个通道：`SendChannel`，可用于向协程中发送数据，lambda表示式：接收者类型是`ActorScope`，该接口中提供一个`channel`常量，用于接收其他地方向协程通道中发来的数据

  ```
  fun main() = runBlocking {
      var actor = actor<Int> {
          for (i in channel) {
              print(i)
          }
      }
      repeat(23) {
          actor.send(it)
      }
  }
  ```

* **async函数**

  - 创建一个协程，返回一个 `Deferred`对象，可用于协程的取消等操作
  - 该函数跟`launch()`函数几乎是一摸一样的（在`GlobalScope`中也有），唯一的不同点在于 block lambda这个函数式参数的返回值
    - `launch()`函数直接返回Unit
    - `async()`函数返回一个类型参数T（out位置），这个参数会作为Deferred类中函数的返回值

  ````
  fun main()  = runBlocking {
      val deferred = async() {
          repeat(3) {
              println("1")
          }
          "result"
      }
      println(deferred.await())		//log：result
  }
  ````

* **produce函数**

  * 创建一个新的协程，返回一个通道：ReceiveChannel，可用于接收协程中发出的数据；lambda表示式：接收者类型是`ProducerScope`，该接口中提供一个`channel`常量，用于该协程向外界发送数据

  ```
  fun main()  = runBlocking {
      val receiveChannel = produce<Int> { 
          repeat(5) {
              channel.send(it)
          }
      }
      repeat(5) {
          println(receiveChannel.receive())
      }
  }
  ```

* **runBlocking函数**

  * 创建一个新的协程，并阻塞当前线程直到协程结束，返回lambda表达式最后一行
    - 该函数主要用于 `main` 函数测试、桥接协程（ `suspend` 函数只能协程或 `suspend` 函数中调用，使用该函数来创建一个可以调用 `suspend` 函数的作用域）

  ```
  //作为桥接协程使用
  runBlocking {
      Log.d("MainActivity", "delay 1s")	//打印顺序：1
      delay(1000)
      Log.d("MainActivity", "start new Coroutines")	//打印顺序：2
      launch {
          Log.d("MainActivity", "delay 1s")	//打印顺序：4
          delay(1000)
          Log.d("MainActivity", "another new Coroutines")	//打印顺序：5
      }
      Log.d("MainActivity", "new Coroutines")	//打印顺序：3
  }
  Log.d("MainActivity", "Main Thread")	//打印顺序：6
  ```

* 阻塞线程、不阻塞线程

  * runBlocking函数会阻塞线程，通过无限循环`while(true`，并通过LockSupport.park()上锁

    ```
    // joinBlocking函数
    while (true) {
        @Suppress("DEPRECATION")
        if (Thread.interrupted()) throw InterruptedException().also { cancelCoroutine(it) }
        val parkNanos = eventLoop?.processNextEvent() ?: Long.MAX_VALUE
        // note: process next even may loose unpark flag, so check if completed before parking
        // 主要是这里：外部是一个无线循环，如果完成就退出
        if (isCompleted) break
        // 这里去调了LockSupport
        parkNanos(this, parkNanos)
    }
    ```

  * launch等函数不会阻塞线程，启动协程后直接返回（不过入队完了之后，去掉了LockSupport.unpark()，释放锁，这里不是很懂哦，因为没有看到前面有加锁的操作）

## 协程的执行顺序

* 协程中代码执行是串行的，然后根据协程运行的线程来判断
* 看下面的代码
  * 应该都是在主线程中执行的啊，为什么会这样呢？
  * 原因：
    * 一开始，队列中的只有一个任务：runBlocking这一整个代码块（**队列元素：runBlocking lambda**）
    * 执行这个任务，发现有launch函数，并且他的Dispatcher也是main Thread，然后将它的代码块入队（**队列元素：runBlockin lambda、launch1 lambda**）
    * 第一个任务还没执行完，继续执行，又发现了launch函数，Dispatcher也是main Thread，入队（**队列元素：run Blocking lambda、launch1 lambda、launch2 lambda**）
    * 第一个任务还没执行完，继续执行，发现是一个输出块，直接执行，第一个任务终于执行完了（**队列元素：launch1 lambda、launch2 lambda**）

```
fun main() = runBlocking {
    val job1 = launch {
        repeat(3) {
            println("job1")								// 2
        }
    }
    val job2 = launch {
        repeat(3) {
            println("job2")								// 3
        }
    }
    println("main thread run over here")	// 1
}
```

## 挂起函数

* 挂起函数只能在协程中、或者是挂起函数中被调用，suspend标识一个函数为耗时函数，所以不让我们在其他地方用，以免造成ANR

* 关于协程的挂起（可以理解为这个协程被暂停执行了，得等待挂起函数执行完，才能继续执行）

  * 协程的挂起，由挂起函数实现，本质上就是切线程，并不会阻塞线程
    * 协程在执行suspend函数时，会被挂起，当前线程就去干其他事情了，由suspend函数内部指定的线程来运行这个协程，完毕后，再切回之前那个线程
      * 比如：delay()函数，由内部的DefaultExecutor来执行延迟操作，执行完后再返回
    * 挂起的是协程，不是线程（指的是这个线程不在运行这个协程了）

* 原理分析：以delay()函数为例，其它的挂起函数也是类似的

  * 总结：**delay()函数：在编译时会传入一个Continuation参数，然后根据CoroutineContext的delay扩展属性（就是一个CoutinuationInterceptor，设置了线程调度就是CoroutineDispatcher），去调不同的入队逻辑，delay函数结束，返回一个表示挂起状态的值**
    - **回到状态机逻辑中，判断返回值，如果是挂起状态的值，则退出（这个时候就被“挂起”了）**
    - **CoroutineDispatcher内部的线程池会去队列中取出”延迟任务“，并执行，任务执行完毕，就会调用编译时出入的Continuation的resumeWith()函数恢复协程（并传入执行结果）。这时候逻辑就回到invokeSuspend()函数（结果也传到了这里），继续执行状态机逻辑**
  * 调用链（以主线程为例进行分析）：delay() -> suspendCancellableCoroutine() -> suspendCoroutineUninterceptedOrReturn()，这个方法中主要是两行代码：执行lambda参数，执行cancellable.getResult()去拿返回值
    * ->  先执行参数中传入的lambda -> 根据拿到的delay属性的类型来调不同的实现方法：scheduleResumeAfterDelay() -> Handler的postDelayed()，至此lambda 参数执行结束
    * -> 然后执行cancellable.getResult() -> 这个函数检查是否挂起，挂起成功，直接返回COROUTINE_SUSPENDED（表示挂起状态），至此，delay()函数执行结束
    * 发送到Handler机制中的Message的逻辑：消息的延迟时间到了之后，取出消息并执行 -> resumeUndispatched() -> resumeImpl()，这里将 -> dispatchResume() -> dispatch() -> resume() -> resumeMode() -> Continuation的resume() -> resumeWith()，至此，协程恢复执行
    * delay属性：HandlerContext（主线程）、其它（EventLoopImplBase等），上面分析以主线程的HandlerContext为例

  ```
  // kotlin源码，我们可以看到：delay()函数只有一个参数，并且没有返回值
  public suspend fun delay(timeMillis: Long) {
      if (timeMillis <= 0) return // don't delay
      return suspendCancellableCoroutine sc@ { cont: CancellableContinuation<Unit> ->
          cont.context.delay.scheduleResumeAfterDelay(timeMillis, cont)
      }
  }
  // 上面拿的delay：如果context[ContinuationInterceptor]转型成功，直接返回，否则直接用DefaultDelay
  internal val CoroutineContext.delay: Delay get() = get(ContinuationInterceptor) as? Delay ?: DefaultDelay
  
  // 下面是反编译后的代码（不能直接反编译源码，所以我直接复制，然后放到我自己的文件中反编译得到的，省略部分代码）
  // 可以看到，比源码多了一个Continuation参数，还多了一个返回值
  @Nullable
  public static final Object delay(long timeMillis, @NotNull Continuation $completion) {
      if (timeMillis <= 0L) {
          return Unit.INSTANCE;
      } else {
          ···
          Object var10000 = cancellable$iv.getResult();
          ···
          return var10000;
      }
  }
  ```

* 所以，挂起函数挂起协程，而不会阻塞线程的原因？（如：delay()）

  * 就是切线程，将协程发送到队列中，由指定的线程中去执行了，线程是相互独立的，所以之前的线程继续执行

* 挂起点之后的逻辑必须等待挂起点之前的逻辑执行完之后才能执行，原因？

  * 因为前面的逻辑没有执行完是不会调resume()来恢复协程的

* 可以这样理解：挂起函数将协程分为了多个Continuation，如下图

  ![kotlin协程挂起点的理解](http://m.qpic.cn/psc?/V506ZavZ24MSFB3OyAT51zX1xi0wNZXV/45NBuzDIW489QBoVep5mcVpiN6M*UednHFnH.5v6oBgaGavKJvVoihzzPl37ZmN1vPZlBF8yUDvzfw3ZCWuiad1x7*BwzVOvt2CFYwkdzC4!/b&bo=MgYjBAAAAAADFyE!&rf=viewer_4)

## 协程的执行原理

* 先看一个反编译代码示例

  * 将block lambda转换为了一个Function2的接口，实现了invoke()函数，并且额外提供了两个函数：invokeSuspend()、create()，分别用于存放协程代码、创建协程。并且在invoke()函数中调用了create()、invokeSuspend()执行协程代码

  ```
  // kotlin代码
  launch(Dispatchers.IO) {
  	println("子协程开始执行")
  }
  
  // 反编译代码，主要看整体结构，这里省略掉具体实现
  BuildersKt.launch$default($this$runBlocking, (CoroutineContext)Dispatchers.getIO(), (CoroutineStart)null, (Function2)(new Function2((Continuation)null) {
      int label;
  
      @Nullable
      public final Object invokeSuspend(@NotNull Object var1) {
          ···
      }
  
      @NotNull
      public final Continuation create(@Nullable Object value, @NotNull Continuation completion) {
          ···
      }
  
      public final Object invoke(Object var1, Object var2) {
          ···
      }
  }), 2, (Object)null);
  ```

* **原理：协程内部通过状态机来处理不同的挂起点来实现的**

  * 下面以delay()函数为例，见上面的挂起函数中分析的delay()函数的实现原理
    * 根据标志位判断该执行那个挂起点的内容，然后开始执行，直到挂起函数之前的位置
    * 在挂起函数之前，先将标志位置为下一个挂起点的标志位，然后当delay()函数将协程发送到队列中之后（根据线程来定是哪一个队列），直接返回一个表示挂起状态的值
    * 根据下面的反编译代码可知：该值会直接结束协程
    * 当CoroutineDispatcher指定的线程池（或者是主线程）从队列中取出协程，并调用invokeSuspend()函数，再次对标志位进行判断，从而继续执行下一个挂起点的内容

  ```
  // kotlin代码，这里以delay()函数为例子
  fun main() {
      CoroutineScope(Dispatchers.IO).launch {
          println("协程开始执行")
          println("将要被挂起：第一次")
          delay(100)
          println("第一次 挂起结束，继续执行")
          println("将要被挂起：第二次")
          delay(100)
          println("第二次 挂起结束，继续执行")
          println("将要被挂起：第三次")
          delay(100)
          println("协程执行完毕")
      }
      // 阻塞主线程，等协程所在线程执行完毕
      Thread.sleep(500)
  }
  
  // 反编译代码，这里提一句哈，我感觉这个反编译的代码每个版本都不大一样，不过本质上，没有什么区别，理解流程就行了
  // 下面去除掉main函数的反编译代码
  BuildersKt.launch$default(CoroutineScopeKt.CoroutineScope((CoroutineContext)Dispatchers.getIO()), (CoroutineContext)null, (CoroutineStart)null, (Function2)(new Function2((Continuation)null) {
  		
  		// 状态码，用于更新协程执行：从一个挂起点跳到下一个挂起点
      int label;
  
      @Nullable
      public final Object invokeSuspend(@NotNull Object $result) {
          String var2;
          boolean var3;
          label26: {
          Object var4;
          label25: {
          //这里返回的值，表示协程现在是出于挂起状态
          var4 = IntrinsicsKt.getCOROUTINE_SUSPENDED();
          switch(this.label) {
              case 0:
              ResultKt.throwOnFailure($result);
              var2 = "协程开始执行";
              var3 = false;
              System.out.println(var2);
              var2 = "将要被挂起：第一次";
              var3 = false;
              System.out.println(var2);
              this.label = 1;
              // 如果是挂起状态，直接退出
              // 挂起函数或挂起 lambda 表达式调用时，都有一个隐式的参数额外传入，就是协程本身（Continuation），用于挂起函数恢复协程的执行
              if (DelayKt.delay(100L, this) == var4) {
                  return var4;
              }
              break;
              case 1:
              ResultKt.throwOnFailure($result);
              break;
              case 2:
              ResultKt.throwOnFailure($result);
              break label25;
              case 3:
              ResultKt.throwOnFailure($result);
              break label26;
              default:
              throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
          }
  
          var2 = "第一次 挂起结束，继续执行";
          var3 = false;
          System.out.println(var2);
          var2 = "将要被挂起：第二次";
          var3 = false;
          System.out.println(var2);
          this.label = 2;
          // 如果是挂起状态，直接退出
          if (DelayKt.delay(100L, this) == var4) {
              return var4;
          }
      }
  
          var2 = "第二次 挂起结束，继续执行";
          var3 = false;
          System.out.println(var2);
          var2 = "将要被挂起：第三次";
          var3 = false;
          System.out.println(var2);
          this.label = 3;
          // 如果是挂起状态，直接退出
          if (DelayKt.delay(100L, this) == var4) {
              return var4;
          }
      }
  
          var2 = "协程执行完毕";
          var3 = false;
          System.out.println(var2);
          return Unit.INSTANCE;
      }
  
      @NotNull
      public final Continuation create(@Nullable Object value, @NotNull Continuation completion) {
          Intrinsics.checkNotNullParameter(completion, "completion");
          Function2 var3 = new <anonymous constructor>(completion);
          return var3;
      }
  
      public final Object invoke(Object var1, Object var2) {
          return ((<undefinedtype>)this.create(var1, (Continuation)var2)).invokeSuspend(Unit.INSTANCE);
      }
  }), 3, (Object)null);
  
  // 上面的invokeSuspend函数不好理解的话，可以这样认为：
  public final Object invokeSuspend(Object param1Object) {
      Object object = IntrinsicsKt.getCOROUTINE_SUSPENDED();
      int i = this.label;
  
      if (i == 0) {
          System.out.println("协程开始执行");
          System.out.println("将要被挂起：第一次");
          i= this.label = 1;
          if (DelayKt.delay(1000L, this) == object) {
              return object;
          }
      }
  
      if (i == 1) {
  				System.out.println("第一次 挂起结束，继续执行");
          System.out.println("将要被挂起：第二次");
          i = this.label = 2;
          if (DelayKt.delay(2000L, this) == object) {
              return object;
          }
      }
  
      if (i == 2) {
          System.out.println("第二次 挂起结束，继续执行");
          System.out.println("将要被挂起：第三次");
          i = this.label = 3;
          if (DelayKt.delay(3000L, this) == object) {
              return object;
          }
      }
  
      if (i == 3) {
      		System.out.println("协程执行完毕")
          return Unit.INSTANCE;
      } else {
          throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
      }
  }
  ```

## 协程原理（创建、启动、挂起、恢复、线程调度）

* **创建流程**：真正创建是在下面的启动流程过程中创建的（createCoroutineUnintercepted()）

* **启动流程**：**从launch等构建函数开始，确定context，使用反编译生成的create函数创建对应的Continuation，并通过指定的ContinuationIntercept对Continuation进行包装，然后由包装之后的Continuation来启动协程，将（协程逻辑）lambda 参数入队（队列在CoroutineDispatcher中），并启动线程池，至此，构建函数执行完毕**

  * **被启动的线程池会去队列中取出任务并执行，就会回调到反编译后重写的invokeSuspend函数中，执行状态机逻辑**
  * **状态机逻辑：通过对状态的判断，来执行对应的代码（switch语句）**
  * 函数调用链：launch() -> AbstractCoroutine的start() -> CoroutineStart的invoke()根据CoroutineStart的状态来调用不同的函数 -> (suspend (R) -> T).startCoroutineCancellable()；看下面：

  ```
  // CoroutineStart的invoke()
  when (this) {
      CoroutineStart.DEFAULT -> block.startCoroutineCancellable(receiver, completion)
      CoroutineStart.ATOMIC -> block.startCoroutine(receiver, completion)
      CoroutineStart.UNDISPATCHED -> block.startCoroutineUndispatched(receiver, completion)
      CoroutineStart.LAZY -> Unit // will start lazily
  }
  
  // startCoroutineCancellable()
  createCoroutineUnintercepted(receiver, completion).intercepted().resumeCancellable(Unit)
  ```

  * 先去调了createCoroutineUnintercepted()（在IntrinsicsJvm.kt中能够找到它的实现）
    * 该函数创建一个协程（通过反编译后生成的create()），返回一个Continuation用于协程恢复
  * 接着调了intercepted()，最终会走到interceptContinuation()：如果设置了Dispatcher，则包装之前的Continuation为DispatcherContinuation（将Dispatcher设置进去了），如果没有则直接返回this
  * 最后调用resumeCancellable()：用于启动协程（入队，并启动线程池，然后就返回了）
    * 启动流程中，分为两种情况（指定了Dispatcher会将Continuation包装为DispatchedContinuation，才会走第一种情况）
      * 如果是DispatchedContinuation类型的Continuation，执行：含block lambda被CoroutineDispatcher的传递过程（将block lambda入队了），然后通过CoroutineDispatcher内部的线程池，从队列中取出block lambda，并执行
        * 猜测到这里回去执行反编译生成的invoke()函数了
      * 不是DispatchedContinuation类型的Continuation，执行：resume() -> resumeWith()，这里面也是走的入队流程，因为之前intercepted()进行了拦截的，返回的是包装之后的Continuation
        * 猜测这里的resumeWith()走了invoke()函数
    * 入队之后，通过LockSupport.unpark()保证线程不会阻塞

* **协程的挂起**：由挂起函数实现：见上面挂起函数的实现原理

* **协程的恢复**：由挂起函数实现：见上面挂起函数的实现原理

* **协程的线程调度**：由CoroutineDispatcher实现，不同的Dispatcher会对应不同的队列、线程，lambda参数（协程逻辑）被发送到对应的队列，然后对应的线程会将lambda参数从队列中取出来执行

  * 感觉好多的框架的线程切换都是这么实现的，EventBus也是类似的

## 协程执行在哪个线程

* 跟Dispatcher有关，但是，一些挂起函数，内部会进行处理，导致协程从挂起到恢复的时候，就已经不在由Dispatcher指定的线程执行了
  * 如delay()函数，如果挂起前去拿Dispatcher并转型为Delay失败，则使用默认的DefaultExecutor，所以协程恢复后，是运行在DefaultExecutor中的
  * 不过一搬是会转型成功的
* 并且Dispatcher可以被覆盖，因为CoroutineContext可以被覆盖

## 常见的类

![Kotlin常见类结构图](https://feishu.processon.com/feishu/view/link/605af20ae4b03246593ccb8f)

* **CoroutineScope**（接口）：协程的作用域，只包含了一个CoroutineContext。分了一下，可以说有三种作用域

  * 顶层作用域：没有父协程所在的作用域
    * 创建
      * GlobalScope：object单例（全局作用域：EmptyCoroutineContext）
      * MainScope()：CoroutineContext为：SupervisorJob()（主从作用域类型的Job） + Dispatchers.Main
      * CoroutineScope()：context中如果没有job，会用构造函数Job()创建一个新的
  * 协同作用域：父子协程，子协程所在的作用域默认为协同作用域
    * 子协程的取消是双向传递的
      * 该协程抛出未捕获异常，父协程会被取消，相应的，与它同级的该父协程下的其他子协程也会被取消
      * 该协程取消，它的子协程都会被取消
    * 创建
      * 协程构建器创建的子协程，作用域都属于协同作用域（因为协程构建器都是CoroutineScope的扩展函数）
      * coroutineScope()：suspend函数，会挂起当前协程，创建一个CoroutineScope，继承外部作用域的context，但Job是用的自己的ScopeCoroutine
  * 主从作用域：父子协程，不过子协程的取消是向下单向传递的
    * 该协程抛出未捕获异常，父协程不会被取消，与它同级的子协程不会被取消
    * 该协程抛出未捕获异常，它的子协程会被取消
    * 创建
      * supervisorScope()：suspend函数，会挂起当前协程，创建一个CoroutineScope，继承外部作用域的context，但Job是用自己的SupervisorJob

* **CoroutineContext**（接口）

  * 协程运行的上下文，它是元素实例的索引集（set和map之间的混合），（EmptyCoroutineContext表示一个空的协程上下文）

  * 我的理解：一个map型的数据结构，这个map有点奇怪：Key、Element相互映射

    * Key<E: Element>：比如说Job的Key为：Key<Job>，所以一个协程中的Job，可以这样拿到：coroutineContext[Job]
    * Element：所有元素都继承它

  * 内部重载了plus运算符，用于合并两个CoroutineContext（`+`号右边的会覆盖掉左边的）

    * 关于合并

  * 常用元素：Job、CoroutinuationInterceptor、CoroutineExceptionHandler、CoroutineDispatcher等

  * **一个协程的上下文**

    * 一个协程的上下文 = CoroutineScope中指定的上下文 + 构建函数出入的context参数 + 协程Job（AbstractCoroutine内部的context = parentContext + this，而AbstractCoroutine继承了Job，this就是这个Job）

      * 子协程没有指定CoroutineScope则context从是父协程的CoroutineScope继承而来

      ```
      CoroutineScope(Dispatchers.IO).launch() {
      	println(coroutineContext[ContinuationInterceptor])
      }
      ```

    * 所以，根据context的合并规则，在构建协程时，可以得出协程的调度器到底是哪个

* **ContinuationInterceptor**：协程拦截器，在协程执行之前包装协程，用于拦截协程的启动

* **CoroutineDispatcher**：属于一种协程拦截器（CoroutineIntercepter的子类），指定协程在哪个线程上运行

  * 实现原理：启动协程时，将创建好的Continuation进行包装（将CoroutineDispatcher设置进去了，得到的是一个：DispatchedContinuation），然后使用通过包装后的对象来启动协程，这样在任务入队的时候，调的dispatcher就是我们指定的了
  * 如果不指定，则有两种情况
    1. 当该协程的context不为Dispatchers.Default，并且ContinuationInterceptor为空，则用Dispatchers.Default
    2. 直接用合并了父协程的context的context

  * **受限调度器**
    * `Dispatchers.Default`
      - 没有指定，则使用这个值。它由JVM上的共享线程池支持。默认情况下，此调度程序使用的最大并行度等于CPU内核数，但至少为两个
      - 根据情况使用这两者之一：CommonPool（线程池）、DefaultScheduler（单线程）
    * `Dispatchers.IO`
      - 与 `Dispatchers.Default` 调度程序共享线程，因此使用 `withContext（Dispatchers.IO）{...}` 不会导致实际切换到另一个线程
      - LimitingDispatcher（线程池、ConcurrentLinkedQueue<Runnable>）
    * `Dispatchers.Main`
      -  `Main` 线程。通常，此类调度程序是单线程的
         - 要使用这个调度器，需要在项目中添加相应的依赖，如安卓应该添加：`kotlinx-coroutines-android`
      -  MainCoroutineDispatcher -> HandlerContext（通过handler实现）
  * **非受限调度器**
    - `Dispatchers.Unconfined`
      - 在第一个挂起点之前，协程运行在调用者（启动协程的）的线程上，当协程在中断之后恢复时，运行在哪个线程完全由所调用的挂起函数决定

* **Job**：封装了协程中需要执行的代码逻辑，具有生命周期，可以取消。完成时没有返回值（结果）

  - 是可以代表一个协程的生命周期？

  * 三种状态：

  | State                            | isActive | isCompleted | isCancelled |
  | -------------------------------- | -------- | ----------- | ----------- |
  | _New_ (optional initial state)   | `false`  | `false`     | `false`     |
  | _Active_ (default initial state) | `true`   | `false`     | `false`     |
  | _Completing_ (transient state)   | `true`   | `false`     | `false`     |
  | _Cancelling_ (transient state)   | `false`  | `false`     | `true`      |
  | _Cancelled_ (final state)        | `false`  | `true`      | `true`      |
  | _Completed_ (final state)        | `false`  | `true`      | `false`     |

  状态的演变如下：其实也比较简单啦~

  ```
                                         wait children
   +-----+ start  +--------+ complete   +-------------+  finish  +-----------+
   | New | -----> | Active | ---------> | Completing  | -------> | Completed |
   +-----+        +--------+            +-------------+          +-----------+
                    |  cancel / fail       |
                    |     +----------------+
                    |     |
                    V     V
                +------------+                           finish  +-----------+
                | Cancelling | --------------------------------> | Cancelled |
                +------------+                                   +-----------+
  ```

* **Deferred**：Job的子类，完成时有结果

* **CoroutineStart**：一个枚举类，内部有4个实例，是协程的启动选项，定义了协程的执行时机

  * DEFAULT：立即根据coroutineContext来调度协程执行
    * 调的resumeCancellable函数，可以取消，设置了resumeMode = MODE_CANCELLABLE，取消的时候回去判断
  * LAZY：懒加载（如：用launch创建协程，指定start为LAZY，则在调用了Job.start()、Job.await()之后才会启动）
    * 启动的时候只是创建了一个LazyActorCoroutine（重写了onStart()，用于启动协程），然后就直接返回，并没有启动
    * 当调用Job.start()之后，会对状态进行判断，非active状态，去调LazyActorCoroutine的onStart()启动协程
  * ATOMIC：原子地（以不可取消的方式）根据coroutineContext来调度协程执行
    * 直接去调resume，而不是resumeCancellable，是不可取消的，没有设置resumeMode
  * UNDISPATCHED：立即执行协程直到它在当前线程中的第一个挂起点，就好像协程是使用Dispatchers.Unconfined启动的。 但是，当协程从暂停状态恢复时，它会根据CoroutineDispatcher在其上下文中进行调度
    * startCoroutineUninterceptedOrReturn直接调的反编译生成的invoke函数，直接启动，挂起函数内部会对根据Dispatcher对线程进行切换，所以恢复的时候就用的是我们通过Dispatcher指定的线程

  ```
  public operator fun <T> invoke(block: suspend () -> T, completion: Continuation<T>) =
      when (this) {
          CoroutineStart.DEFAULT -> block.startCoroutineCancellable(completion)
          CoroutineStart.ATOMIC -> block.startCoroutine(completion)
          CoroutineStart.UNDISPATCHED -> block.startCoroutineUndispatched(completion)
          CoroutineStart.LAZY -> Unit // 直接返回，job.start的时候在启动
      }
      
  // UNDISPATCHED
  internal fun <T> (suspend () -> T).startCoroutineUndispatched(completion: Continuation<T>) {
      startDirect(completion) { actualCompletion ->
          withCoroutineContext(completion.context, null) {
              startCoroutineUninterceptedOrReturn(actualCompletion)
          }
      }
  }
  ```

  

* **Continuation<in T>**：表示挂起点之后的延续，该挂起点返回类型为`T`的值，在代码中，它用于控制协程的启动

  - 封装了协程的代码运行逻辑，提供了`resumeWith()`函数用于启动协程
    - resumeWithException()：Continuation的扩展函数，用于恢复一个协程，并传递一个异常（返回一个异常对象）
    - resume()：Continuation的扩展函数，用于恢复一个协程，并传递一个值（返回值）
  - 继承关系：`SuspendLambda -> ContinuationImpl -> BaseContinuationImpl -> Continuation`
    - 注解中讲：用于命名挂起函数的状态机，应该去扩展ContinuationImpl，而它只有一个子类：SuspendLambda
  - 协程的代码，反编译后，创建的匿名内部类：应该是去继承了SuspendLambda类，实现了Function2接口（以往的代码反编译后可以看出，不过1.3版本的代码反编译后不是那么明显，还是能看出来的）

* **ContinuationImpl**：Continuation的实现类，内部实现了resumeWith()函数

  * 内部是一个while(true)无限循环，不断的去调用invokeSuspend()函数（协程中，反编译会实现该函数，该函数内部通过状态机来实现），这个循环没咋弄懂？
    * 如果返回COROUTINE_SUSPENDED（表示挂起状态），则直接return退出循环

* 常见类结构图，如下

  ![Kotlin常见类结构图](http://m.qpic.cn/psc?/V506ZavZ24MSFB3OyAT51zX1xi0wNZXV/45NBuzDIW489QBoVep5mcYs.5LG.Oynt8NdL4*OIGWQFpPakSTgcMa8wcHsXL576Riv7eC5Dex6IDdE0VMNNgxxxp85w0dBvdbUqoIyX8PA!/b&bo=igw4BAAAAAADJ7g!&rf=viewer_4)

## 常用函数

* withContext

  - 使用给定的协程上下文调用指定的暂停块，暂停直到完成，然后返回结果（context也会算上调用它的接受者的context）

    - 该函数的最后一行代码作为一个结果返回
    - 不会创建协程，但可以指定context来覆盖之前的context（可以使用这个来达到切换线程的目的）
    - 在挂起的时候，如果当前协程的Job被取消或完成，那么该函数将立即抛出CancellationException返回

    ```
    //这里withContext代码块换为其他的协程代码来执行，将会是不一样的效果
    fun main(args: Array<String>) = runBlocking<Unit> {
        println("我可以执行了吗？")
        withContext(Dispatchers.Default) {
            println("等我执行完了来~")
        }
    
        println("终于可以执行了")
    }
    
    log:
    我可以执行了吗？
    等我执行完了来~
    终于可以执行了
    ```

* coroutineScope()：suspend函数，会挂起当前协程，创建一个CoroutineScope，继承外部作用域的context，但Job是用的自己的ScopeCoroutine

* supervisorScope()：suspend函数，会挂起当前协程，创建一个CoroutineScope，继承外部作用域的context，但Job是用自己的SupervisorJob

* delay()：挂起当前协程一段时间，不会阻塞线程，并在指定时间后恢复它

  * 可以取消：同上
  * 协程的挂起：由``suspendCancellableCoroutine()`实现
  * 协程的恢复：发送一个延迟任务到DefaultExecutor队列尾部，时间到了就执行任务（任务内容是恢复协程）
    * 注意：协程的恢复是在也是跟我们指定的线程池

* join()：挂起当前协程，让接收者job执行，直到执行完毕

  * 也可以被取消，同delay()

* await()：挂起当前协程，让接收者Deferred执行，直到执行完毕

* yield()：挂起当前协程，让其他协程执行，执行完毕后，恢复当前协程（其实是让当前协程的线程去执行这个线程上的其它协程）

  * 也可以被取消，同delay()
  * 不过是对运行在同一个线程上的协程才有用，原因如下：
    * 内部会进行Dispatcher的检查，没有Dispatcher，直接返回
    * 有Dispatcher，则将当前协程分发到Dispatcher的队列中去（尾插法），在Dispatcher空闲的时候会进行处理

* suspendCoroutineUninterceptedOrReturn()：获取当前Continuation实例，并挂起当前协程，lambda参数执行完后返回

* suspendCoroutine()：通过suspendCoroutineUninterceptedOrReturn()实现

  * 获取当前Continuation实例，并暂停当前正在运行的协程
  * 在此函数中， Continuation.resume()和Continuation.resumeWithException()可以用于返回该函数，并恢复协程，之后就不能再调resume了，会抛出一个IllegalStateException异常

* 是suspendCancellableCoroutine()：通过suspendCoroutineUninterceptedOrReturn()实现

  * 挂起时，可以被取消，也是抛出一个CancellableException异常

## 父子协程

* 当一个协程在其它的CoroutineScope中启动的时候，它将通过CoroutineScope.coroutineContext来继承上下文，并且这个新协程的 Job 将会成为父协程Job的子Job

  * 在启动协程时，会将该协程的Job attch到 父协程的Job上。内部通过链表实现，将job封装为了一个节点
  * initParentJob() -> initParentJobInternal()

* 只要不是使用的 `GlobalScope` 来创建（使用`CoroutineScope`来创建），那么这两个协程就叫做父子协程了（`GlobalScope.launch{}` 和 `GlobalScope.async{}` 新建的协程是没有父协程的，是全局的，在协程逻辑执行完之前除了手动 `cancel`，不然是不会停止的）

* **特性**

  * 子协程会继承上下文（也就继承了协程调度器）
  * 父协程手动调用`cancel()`或者异常结束，会立即取消它的所有子协程
    * 因为子协程的Job会成为父协程Job的子Job，cancel函数在取消的时候，会去遍历，然后一个个的取消
  * 父协程必须等待所有子协程完成（处于完成或者取消状态）才能完成
    * 子协程完成时，会去更新自己的状态，父协程只有在所有子协程都完成的情况下才会处于完成状态，而父协程只有在处于完成状态、取消状态时才能结束
  * 子协程抛出未捕获的异常时，默认情况下会取消其父协程（协同作用域下）
    * 这也会导致其它子协程被取消
    * 但是在主从作用域下，协程的取消（因异常而取消）是不会影响父协程的，会影响它的子协程

* **原理**

  * **建立父子关系**

    ```
    launch()内部实现：
    		-> newCoroutineContext()内部继承了父协程的context，并默认使用Dispatchers.Default作为协程调度器
    		-> 创建协程
    		-> coroutine.start(start, coroutine, block)启动协程
    			-> initParentJob() -> initParentJobInternal()
    					-> parent.attachChild(this)建立父子关系
    						-> invokeOnCompletion()：将父协程封装为ResumeAwaitOnCompletion作handler节点添加到子协程的state.list，然后在子协程完成时会通知handler节点调用父协程的resume(result)方法将结果传给父协程，并恢复父协程继续执行await挂起点之后的逻辑
    ```

  * **父协程手动调用`cancel()`或者异常结束，会立即取消它的所有子协程**

    ```
    job的cancel()内部实现
    	-> cancelInternal() -> cancelImpl() -> makeCancelling()
    		-> tryMakeCancelling() -> notifyCancelling()
    			-> notifyHandlers()：这里会调用所有子协程绑定的 ChildHandleNode.invoke(cause) -> childJob.parentCancelled(parentJob) 来取消所有子协程
    			-> cancelParent()：取消父协程
    ```

  * **父协程必须等待所有子协程完成（处于完成或者取消状态）才能完成**

    ```
    协程的完成通过`AbstractCoroutine.resumeWith(result)`实现
    	-> makeCompletingOnce() -> tryMakeCompleting() -> tryMakeCompletingSlowPath()
    		-> tryWaitForChild()：内部递归地调用invokeOnCompletion()：添加父节点到子协程的 state.list 中，当子协程完成时会调用 ChildCompletion.invoke()-> continueCompleting()，当子协程都完成了之后，再完成自己
    			-> tryFinalizeFinishingState()
    ```

## 原理分析

* **协程挂起、恢复**

  协程的挂起通过`suspend`挂起函数实现（自带挂起功能的挂起函数），协程的恢复通过`Continuation.resumeWith`实现

  * 挂起：协程挂起就是协程挂起点之前（到上一个挂起点）逻辑执行完成（`invokeSuspend()`），协程的运算关键方法`resumeWith()`执行完成，线程继续执行往下执行其他逻辑
    * 挂起函数将执行过程分为多个`Continuation`片段，并且利用状态机的方式保证各个片段是顺序执行的。每一个挂起点和初始挂起点（相邻两个挂起点）对应的`Continuation`都会转化为一种状态
    * 挂起点后面的代码只能在挂起函数执行完后才能执行
  * 恢复：协程恢复只是跳转到下一种状态中（`await()`内部调的是`JobSupport.awaitSuspend()`）
    * `await()`函数为例：将 launch 协程封装为`ResumeAwaitOnCompletion`作为`handler`节点添加到`aynsc`协程的`state.list`，然后在`async`协程完成时会通知`handler`节点调用`launch`协程的`resume(result)`方法将结果传给`launch`协程，并恢复`launch`协程继续执行`await`挂起点之后的逻辑
      * 上面的示例是：`launch`启动的协程中再使用`async`启动了一个协程。其中，`launch`与`async`启动的协程，个人认为可以替换为父子协程

* **协程的线程调度**

  * 通过拦截器来实现的，在协程启动过程（`startCoroutineCancellable()`）中，添加了拦截器

    ```
    //startCoroutineCancellable()内部实现
    createCoroutineUnintercepted(receiver, completion).intercepted().resumeCancellable(Unit)
    ```

    * `intercepted()`中使用`CoroutineDispatcher`的`interceptContinuation()`方法将原来的`Continuation`包装到了`DispatchedContinuation`中，拦截所有的协程运行操作

      ```
      //context[ContinuationInterceptor]存的就是CoroutineDispatcher
      public fun intercepted(): Continuation<Any?> =
          intercepted
              ?: (context[ContinuationInterceptor]?.interceptContinuation中(this) ?: this)
                  .also { intercepted = it }
                  
      //interceptContinuation
      public final override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
          DispatchedContinuation(this, continuation)
      ```

    * `DispatchedContinuation`拦截协程启动、恢复部分如下（`resumeCancellable(Unit)`、`resumeWith(Result)`）

      * 内部维护线程池`CommonPool`，将`runnable`进行包装，交给`Executor`处理（`(pool ?: getOrCreatePoolSync()).execute(wrapTask(block))`）

      ```
      override fun resumeWith(result: Result<T>) {
          ···
          //是否需要线程调度
          if (dispatcher.isDispatchNeeded(context)) {
              ···
              //将协程的运算分发到另一个线程
              dispatcher.dispatch(context, this)
          } else {
          	//如果不需要调度，直接在当前线程执行协程运算：非受限调度器Dispatchers.Unconfined的处理
              executeUnconfined(state, MODE_ATOMIC_DEFAULT) {
                  withCoroutineContext(this.context, countOrElement) {
                      continuation.resumeWith(result)
                  }
              }
          }
      }
      @Suppress("NOTHING_TO_INLINE") // we need it inline to save us an entry on the stack
      inline fun resumeCancellable(value: T) {
      	//是否需要线程调度
          if (dispatcher.isDispatchNeeded(context)) {
              ···
              //将协程的运算分发到另一个线程
              dispatcher.dispatch(context, this)
          } else {
          	//如果不需要调度，直接在当前线程执行协程运算：非受限调度器Dispatchers.Unconfined的处理
              executeUnconfined(value, MODE_CANCELLABLE) {
                  if (!resumeCancelled()) {
                      resumeUndispatched(value)
                  }
              }
          }
      }
      ```

## 封装异步回调

**一般封装为挂起函数，总结一下：封装异步回调的方法：使用suspengCoroutine()系列函数进行包装异步逻辑，然后在异步逻辑执行完毕时，调用resume()系列函数进行恢复**

* 使用函数`suspendCoroutine()`进行网络请求

  ```
  private suspend fun <T> Call<T>.await(): T = suspendCoroutine { continuation ->
       enqueue(object : Callback<T> {
          override fun onResponse(call: Call<T>, response: Response<T>) {
              continuation.resume(response.body())	// 恢复协程
          }
          override fun onFailure(call: Call<T>, response: Response<T>) {
          		//	恢复协程
              continuation.resumeWithException(NetworkErrorException("can connect net"))
          }
      })
  }
  ```

## 协程的并发

* **Mutex**

  * 使用自旋 + CAS代替了阻塞操作

    * 当一个协程拿到这个锁后，其他协程再尝试去获取的话，就会被挂起（协程挂起时，线程可以去执行其他的工作）
    * `withLock{}`函数用于获取锁

    ```
    import kotlinx.coroutines.*
    import kotlinx.coroutines.sync.Mutex
    import kotlinx.coroutines.sync.withLock
    
    fun main() {
        val mutex = Mutex()
        val count = Count(0)
        val job1 = CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                repeat(5) {
                    changeCount("job1", count)
                }
            }
        }
        val job2 = CoroutineScope(Dispatchers.Default).launch {
            mutex.withLock {
                repeat(5) {
                    changeCount("job2", count)
                }
            }
        }
        Thread.sleep(500)
    }
    
    public fun changeCount(name: String, count: Count) {
        println("$name: ${count.count++}")
    }
    
    class Count(var count: Int)
    ```

  * 原理：如果该锁的owner不是当前协程，就自旋执行CAS操作去更新owner

* **协程局部数据**

  * 类似java线程的ThreadLocal，通过扩展函数：`ThreadLocal.asContextElement()`实现

  * 启动和恢复时保存`ThreadLocal`在当前线程的值，并修改为 value，挂起和结束时修改当前线程`ThreadLocal`的值为之前保存的值

    ```
    runBlocking {
        val threadLocal = ThreadLocal<String>().apply {
            set("main thread value")
        }
        Log.d("zwh_tag", threadLocal.get())
    
        CoroutineScope(Dispatchers.IO).launch(threadLocal.asContextElement("IO thread value")) {
            Log.d("zwh_tag", threadLocal.get())
            yeild()
            Log.d("zwh_tag", threadLocal.get())
        }
        Log.d("zwh_tag", threadLocal.get())
    }
    ```


* **结构化并发**：就是指在一个协程作用域内开启多个字协程进行并发行为，父协程会限制子协程（见上面的[父子协程](#父子协程)）

  * `async` 异步并发执行

  ```
  fun main(args: Array<String>) = runBlocking<Unit> {
      val df1 = async { test1() }
      val df2 = async { test2() }
      println("${df1.await()} + ${df2.await()}")
  }
  ```

  * 但是这样也存在一定的问题，如果 `df1.await()` 抛出异常，但是 `df2` 不会受到影响，这样是不符合逻辑的
  * 这时就可以使用结构化的并发了，将 `df1`、`df2` 工作代码置于同一个作用域 `coroutineScope {···}` 上，即可实现结构化并发。
    * 作用域内的协程称为外部协程的子协程，`async` 协程构建器就像 `luanch` 一样，变成了协程作用域上的一个扩展
    * 当外部协程被取消或者子协程异常时，作用域中的所有子协程都会被取消

  ```
  fun main(args: Array<String>) = runBlocking<Unit> {
      coroutineScope {
          val df1 = async { test1() }
          val df2 = async { test2() }
          println("${df1.await()} + ${df2.await()}")
      }
  }
  
  //或者在 suspend 函数中使用 
  suspend fun test() {
      coroutineScope {
          val df1 = async { test1() }
          val df2 = async { test2() }
          println("${df1.await()} + ${df2.await()}")
      }
  }
  ```

  * 也可以使用 `supervisorScope` 函数来达到相同的效果
  * `coroutineScope` 和 `supervisorScope` 的区别在于：`coroutineScope` 会在任意一个协程发生异常后取消所有的子协程的运行，而 `supervisorScope` 并不会取消其他的子协程
    * 异常的自定义处理可以使用 `SupervisorJob` 来实现

## 异步流Flow

* 优点：简单易用，解决回调地狱（跟RxJava类似的响应式编程，但是使用上却要简单得多），解决内存泄漏的问题，配合协程使用，切换线程更方便，与Android联系跟密切（如：Room支持返回 `Flow` 类型以获取实时更新）

* 应用场景：开始/停止数据的产生来匹配观察者（产生多个值）

* 用于返回多个值

* **异步流的理解**：类似RxJava的响应式编程，一个上游、下游，负责数据的传送（emit、collect等函数），并提供了很多的操作符进行处理等

  * **所谓的“冷流”**
    * 就类似序列sequence，只有调用了末端操作符，前面的操作才会执行（一种延迟的现象）
      * 以观察者模式来说：当你订阅了事件，我才开始产生事件
      * 而“热流”：是我都在产生事件，而你只管订阅就行了
    * Flow中也是如此，末端操作符：collect、toList、toSet、first、single、reduce、fold
  * **连续的流**
    * 顾名思义：每次从上游下发一个数据到下游，都会一直连续执行中间的操作符。一个数据下发完成了，才能下发另一个数据

  * 对于Kotlin自己的同步序列：Sequence
  * 流使用emit函数发射值
  * 流使用collect函数收集值
  * 代码示例：（先看看网上有拿Flow干嘛的。这里写一个好点的代码，比如网络请求返回数据然后进行UI处理之类的）

  ```
  //示例
  ```

  

* **创建流Flow**

  * flow{···}函数

    * `flow { ... }` 函数构建块中的代码可以挂起，因为参数类型是一个suspend修饰的函数类型

    * 该函数的代码遵循上下文保存属性：不允许从其他上下文中发射

      ```
      fun simple(): Flow<Int> = flow { // 流构建器
          for (i in 1..3) {
              delay(100) // 假装我们在这里做了一些有用的事情
              emit(i) // 发送下一个值
          }
      }
      ```

  * flowOf()函数：创建一个发射固定值集的流

    ```
    val flow: Flow<Int> = flowOf(1, 2, 3)
    ```

  * asFlow()扩展函数：将各种集合、序列转换为流

    ```
    (1..3).asFlow()
    ```

* **流的上下文**

  * flowOn函数：更改Flow发射的上下文（内部实现了缓存），如果上游的Flow，改变了上下文中的CoroutineDispatcher，则该函数会创建另一个协程

  * 注意正常情况中，协程通过withContext改变上下文的方式不适用于此处，会报错。因为`flow{···}`代码块中不允许从其他上下文中发射

    ```
    fun simple(): Flow<Int> = flow {
        for (i in 1..3) {
            Thread.sleep(100) // 假装我们以消耗 CPU 的方式进行计算
            log("Emitting $i")
            emit(i) // 发射下一个值
        }
    }.flowOn(Dispatchers.Default)	//这里就改变了协程调度器
    ```

* **流的取消**

  * Flow的收集操作可以在：当Flow在一个可取消的挂起函数（delay()）中挂起时，取消，否则不能
  * 在协程处于繁忙循环的情况下，必须明确检测是否取消。 可以添加 `.onEach { currentCoroutineContext().ensureActive() }`，或者是cancellable操作符来进行
    * 这样在collect收集流的时候就可以根据条件调用cancel()进行取消流（会抛出一个异常）

* **流的完成**

  * 命令式finally块：使用try/catch···finally块包一下Flow的收集操作，当收集操作完成时，就会走到finally块中的代码
  * 声明式：onCompletion过渡操作符：当流完全收集时就会调用，lambda表达式中有个可空的参数Throwable，可以区分是正常完成还是异常结束
    * 发生了 异常可以使用catch操作符进行处理

* **流的异常**

  * 处理就跟普通的kotlin代码一样，使用try/catch进行处理
    * 可以使用check指定异常的信息，这样发生异常的时候，catch中的Throwable内容就是你指定的异常信息
  * Flow必须对异常透明，这里不是很懂
  * catch：保持此异常的透明性，并运行封装其异常处理行为。但是，只会捕获上游的异常
  * check：根据指定条件抛出异常
    * 使用catch的emit可以将异常转换为值发射出去
    * 可以使用throw重新抛出异常
    * 声明式的捕获？没咋懂

* **操作符**

  **过渡操作符：返回Flow对象，用于处理上游发送出来的数据，返回一个新的转换后的Flow，但并不会马上进行转换，类似冷流，冷操作符**

  * **缓冲**

    * buffer：提供一个缓冲区，用于存放从上游发送来的数据，即使当前的这个数据没有被处理完（因为Flow是连续的，发送数据是一个一个的发送、处理的，必须等到处理的完成后，才可以发送下一个，这时就可以使用buffer来缩短一下时间）
    * conflate：合并：这个运算符不是很懂，目前的理解来看就是指处理最新的（当来了新值，就替换、或者是丢弃掉缓冲中还没有被处理的数据）
    * collectLatest：处理新值，当接收到的数据collect等其他代码块中的逻辑还没有执行完时，又来了一个新值，那么，丢弃掉当前这个数据的逻辑，开始处理新值

  * **组合**

    * zip：用于合并两个Flow的数据（一个对一个的合并）
    * combine：也是合并，不过是两个Flow中有一个发射了新数据就会进行合并，不管另一个Flow中的发射了新值，还是之前发出的旧值

  * **展平流**

    存在Flow<Flow<T>>类型的流，使用下面的三个操作符进行展开

    * flatMapConcat：一个流一个流的处理
    * flatMapMerge：把内部所有的流传入的值合并到单个流中（接受一个可选的并发参数，该参数用于限制同时收集的并发流的数量（默认情况下等于 DEFAULT_CONCURRENCY））
    * flatMapLatest：当在处理一个流的值时，另外的流发出来了新值，那么当前流的处理逻辑会被取消，当前流就会被取消

  * **其他，有些在操作符之前就已经进行讲解，这里就不再列举出来了**

    * onEach：对流中每个数据进行处理
    * map：将传入流映射为结果值
    * transform：实现数据的转换，可以代替map等操作符，也可以实现更复杂的功能
    * take：只传递指定参数数量的值，参数为长度（限长操作符）
    * onStart：流开始发送时回调
    * onCompletion：流发送完成时回调（最后一个数据发送完时就会回调，与末端操作符相比时间间隔比较短，所以有可能在ui上进行的操作，末端操作符最后一个数据不会显示，而会显示onCompletion中回调的数据）

  **末端操作符：返回Unit，该操作符执行时，前面的过渡操作才会执行，用来帮助下游收集数据**

  * collect：收集Flow中发送来的数据
  * launchIn：在单独的协程中启动流的收集
  * toList：转换为List集合
  * toSet：转换为Set集合
  * first：获取一个值
  * single：用于确保流发出单个值
  * reduce：将流还原为某个值（根据我们定义的lambda）

* **Flow原理**

  * 内部通过Channel实现的，emit发射数据就是调的channel.send()

## 通道Channel

**类似BlockingQueue，不过send、receive操作不会像put、take这种阻塞操作，就是一个生产者-消费者模型**

* 应用场景：在协程之间传递数据

* **Chanel创建**

  * 工厂方法：Channel()：根据传入的缓冲区大小，创建不同类型的Channel（缓存大小是看对应的Channel内部的实现，而不是我们指定的）

    * 返回Channel类型，其它的创建方法都是调的合格工厂方法

    ```
    public fun <E> Channel(capacity: Int = RENDEZVOUS): Channel<E> =
        when (capacity) {
            RENDEZVOUS -> RendezvousChannel()
            UNLIMITED -> LinkedListChannel()
            CONFLATED -> ConflatedChannel()
            BUFFERED -> ArrayChannel(CHANNEL_DEFAULT_CAPACITY)
            else -> ArrayChannel(capacity)
        }
    ```

  * CoroutineScope.produce()：在协程中使用，创建一个协程，参数包含一个CoroutineContext

    * 返回ReceiveChannel类型，所以send应该在lambda表达式中调用
    * 比较便捷的创建一个协程和channel，用于其它协程接收该协程发出的数据

  * CoroutineScope.actor()：在协程中使用，创建一个协程

    * 返回SendChannel类型
    * 比较便捷的创建一个协程和channel，用于该协程向其它协程发送数据

  * ticker()：创建一个延迟发送的Channel

    * 返回ReceiveChannel类型

* **Channel处理**

  * send()：向Channel中发射数据，如果数据已经满了，则挂起协程

  * receive()：从Channel中接收数据，会删除元素，如果数据为空，则挂起协程

  * consumeEach：从Channel中消费数据

    * 还有一系列的consumeXxx()系列函数

  * close()：关闭Channel

    * 通过向Channel中发送一个特殊的close标记，收到这个标志，就会关闭

  * 迭代Channel：(注意扇入的情况：多个协程的数据不一定全都发送完了)

    ```
    // 通过迭代器
    val iterator = channel.iterator()
    while (iterator.hasNext()) {
    		testChannelData.value = iterator.next()
    }
    
    // 或者通过for each，本质也是迭代器
    for (i in channel) {
    	testChannelData.value = channel.receive()
    }
    
    // 或者通过repeat函数
    repeat(10) {
    	delay(500)
     	testChannelData.value = channel.receive()
    }
    ```

* **Channel分类、特性**

  * channel是先进先出的：这点是对于协程而言的，谁先来，谁就先拿到数据
  * 不带缓冲
    * channel只在发送方、接收方分别同时进行发送、接收操作时才能进行数据的传输
    * 先调用的一方就会被挂起，直到另一方被调用？
  * 带缓冲
    * channel允许发送方发送数据，直到缓冲区满了才会被挂起
      * 接收方呢？
    * 创建协程时，通过capacity参数指出缓冲区大小（Channel()、produce()）
      * 根据你指定的capacity，对找是否符合条件，然后创建对应条件的channel（并不是我们指定多少的capacity就是多少）
  * 计时器通道
    * 通过ticker()创建
  * SendChannel：只发送数据
  * ReceiveChannel：只接收数据

* 场景

  * 扇入（fan in）：多个协程发送数据到同一个Channel
  * 扇出（fan out）：多个协程从同一个Channel中接收数据
  * 管道：我的理解就是自己写的两个Channel，然后拼接起来

* **原理**

  * 内部维护队列

## 异常处理

* **协程的构建器分为两种**

  * 自动传播异常的（launch、actor）：将异常视为未捕获异常（try-catch捕获不了的，只能报错；CoroutineExceptionHandler可以捕获）
  * 向用户暴露异常的（async、produce）：依赖用户来最终消费异常（可以用try-catch进行捕获：但是只能在try-catch中通过调用await()、receive()才能捕获）

* **协程异常处理器（**CoroutineExceptionHandler）：是CoroutineContext的子接口的实现类

  * 使用：创建CoroutineExceptionHandler对象，在构建协程的时候传入（**只能是launch、ator这类自动传播异常的构建器**），当这个协程抛出异常时，会被CoroutineExceptionHandler捕获
  * ps：设置CoroutineExceptionHandler给根部的父协程是没有意义的，当子协程抛出异常，父协程还是会被取消

* **关于CancellationException**：协程内部定义的异常，用来取消协程的，不会报错，还可以捕获

* 当多个子协程抛出多个异常时，取第一个异常进行处理，并将第一个异常之后发生的其它所有异常都作为被抑制的异常绑定到第一个异常中（捕获第一个异常进行打印时，会打印出绑定在一起的异常信息）

* Supervisor job/scope，没理解到

* 捕获全局异常

  * 原理：回调（观察者模式），exceptionHandler被保存在context中，发生异常时，会将异常一直传递到取消协程的函数中（反编译代码中invokeSuspend函数有个try  - catch语句，捕获了所有的异常），先取消协程，然后通知到exceptionHandler的handleException函数中

  ```
  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      LogUtil.log("网络请求发生错误：${throwable.message}")
      netData.value = "网络请求失败，请检查网络连接"
  }
  ```

* [原理](https://www.jianshu.com/p/20418eb50b17)

## Select表达式

* 暂不记录，目前（2021/03/24）还处于实验阶段，后续正式发布再进行记录

## 在Android上的使用

* 注意内存泄漏问题（比如：在activity中，onDestory()中调用cancel()取消协程任务）
* 与Android的联动：Android官网提供的扩展程序：[Android KTX扩展程序](https://developer.android.com/kotlin/ktx?hl=zh-cn#viewmodel)，对协程提供支持，可以更加方便地使用，示例如下：

```
class MyFragment: Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Lifecycle KTX，此范围内启动的协程会在Lifecycle被销毁时取消，不会有内存泄漏问题
        viewLifecycleOwner.lifecycleScope.launch {
            val params = TextViewCompat.getTextMetricsParams(textView)
            val precomputedText = withContext(Dispatchers.Default) {
                PrecomputedTextCompat.create(longTextContent, params)
            }
            TextViewCompat.setPrecomputedText(textView, precomputedText)
        }
    }
}
```

* 结合mvvm + jetpack一起使用，直接起飞！

## 扩展功能

* 使用自定义的线程池
  * 在Executors.kt中提供了很多ExecutorService的扩展函数，用于自定义线程池
  * 或者直接继承CoroutineDispatcher，实现内部逻辑即可

```
val coroutineDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
CoroutineScope(coroutineDispatcher).launch {
    // TODO
}
```

## 疑点、未完成的任务 -> 之后改为常见的理解难点

* 为什么挂起函数一定要在挂起函数或者协程里面被调用？
  - 为了能够在切换线程之后再切回来（见上面挂起函数的原理：需要通过传入的Continuation实例来恢复协程的执行，在协程中调用挂起函数，编译时会自动向挂起函数中传入Continuation实例）
* 挂起的功能由谁实现？我随便创建一个被suspend关键字修饰的函数都可以做到挂起吗？
  - 并不是被suspend关键字修饰了，就是有挂起功能的函数了，如下可知，如果内部没有调用具有挂起功能的suspend函数，那么我们创建的函数就是一个只能在协程中使用的普通的函数
  - 要想一个suspend函数起到挂起的作用，要在内部直接或者间接的调用到一个自带的挂起功能的suspend函数，让这个函数来实现挂起（比如：delay()函数）
* 那这个suspend关键字有什么用呢？
  - 就是一个提醒：函数创建者对调用者的提醒（提醒调用者，我是一个耗时的函数，请在协程中使用我）
* 挂起点？挂起点怎么看的？
  * 协程中有一个挂起函数就表示一个挂起点（可以理解为挂起点把整个协程分为了多个小部分）
* job封装的协程代码逻辑？
* [CoroutineScope](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/index.html).actor()也可以创建新协程
* 两类Continuation？
  * 其中一类 Continuation `BaseContinuationImpl`的`resumeWith`封装了协程的运算逻辑，用以协程的启动和恢复；而另一类 Continuation `AbstractCoroutine`，主要是负责维护协程的状态和管理，它的`resumeWith`则是完成协程，恢复调用者协程。
* 通过一步步的分析，慢慢发现协程其实有三层包装
  * 常用的`launch`和`async`返回的`Job`、`Deferred`，里面封装了协程状态，提供了取消协程接口，而它们的实例都是继承自`AbstractCoroutine`，它是协程的第一层包装
    * 可以完成协程`AbstractCoroutine.resumeWith()`、取消协程等协程状态的操作
  * 第二层包装是编译器生成的`SuspendLambda`的子类，封装了协程的真正运算逻辑，继承自`BaseContinuationImpl`，其中`completion`属性就是协程的第一层包装。
  * 第三层包装是前面分析协程的线程调度时提到的`DispatchedContinuation`，封装了线程调度逻辑，包含了协程的第二层包装。三层包装都实现了`Continuation`接口，通过代理模式将协程的各层包装组合在一起，每层负责不同的功能
* 看官网都说Flow里面的操作符执行很快？究竟是哪种快？为什么？
* flowOn函数：如果上游的Flow，改变了上下文中的CoroutineDispatcher，则该函数会创建另一个协程？要是下游跟上游的协程一样呢？
* Flow的操作符单独调用，不会执行？必须连着调用？

```
CoroutineScope(Dispatchers.Main).launch {
    val flow: Flow<Int> = flowOf(1, 2, 3)
    flow.onCompletion { cause ->
        if (cause != null) log("发生异常导致Flow结束")
        log("Flow完成")
    }
    //上面的log不会打印，只会执行collect收集数据
    flow.collect { value -> log(value.toString()) }
}
```

* conflate没懂，合并在哪里体现？不如说是替换？
* 异常的透明性？不是很懂
  * 流必须*对异常透明*，即在 `flow { ... }` 构建器内部的 `try/catch` 块中[发射](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow-collector/emit.html)值是违反异常透明性的。这样可以保证收集器抛出的一个异常能被像先前示例中那样的 `try/catch` 块捕获。
* Flow操作符本身都不是suspend函数，那么为什么能够在协程中调用？
  * 貌似只有过渡操作符才不是suspend？去看看末端操作符是不是
* Flow中的所有中间操作符，都可以在代码块中调用emit？
* single操作符没咋懂？单个值？不是本来异步流就是单个值的吗？
* 无缓冲的通道：一方先调用函数，会被挂起，当另一方也调用了函数，还会被挂起吗？还是直接恢复了，继续执行？
* 带缓冲的通道：如果缓冲区数据为空，那么接收方执行接收操作还会被挂起吗？
* 协程的取消：通过抛出一个coroutineExceptionHandler来实现
* Job、deferred的api
* 关于try-finally，如果协程被取消，那么 finally块是一定会走到的
* supervisorScope、supervisor job 
* 协程的监督Supervisor job/scope，没理解到
* 共享的可见状态（在多个协程中访问共享变量，就像在多个线程中访问同一个共享变量一样）

## TODO

* 启动过程总结
  * 编译阶段，编译器把协程代码封装成*SuspendLambda*子类，并且根据suspend关键字把代码切割，用状态机来控制代码块执行
  * 启动协程后，通过协程代码实现的*create*接口创建协程实例，调用resumeWith最终执行到协程代码的invokeSuspend
  * 主线程环境下，协程利用主线程Looper+Handler实现任务分发；异步线程环境下，协程维护后台线程池实现任务分发

## 