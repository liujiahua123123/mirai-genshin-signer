package net.mamoe.n

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.html.currentTimeMillis
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.io.File
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.random.Random

/**
 * 这个Task可以用来抢云游戏内测名额
 */
val cdf = SimpleDateFormat("yy/MM/dd HH:mm:ss SSS").apply {
    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
}
suspend fun main() {
    println("Input combo-token [x-rpc-combo_token], use \"cache\" to get token from history")
    val inputComboToken = readLine()!!.trim()
    val cacheFile = File(System.getProperty("user.dir") + "/cacheToken.txt").apply {
        createNewFile()
    }

    val comboToken = if(inputComboToken == "cache"){
        cacheFile.readText().apply {
            if(this.isBlank()){
                error("Could not get token from history")
            }
        }
    }else{
        inputComboToken
    }

    cacheFile.writeText(comboToken)
    println("using combo token=$comboToken")

    val format = SimpleDateFormat("yy/MM/dd HH:mm").apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    val cur =format.format(currentTimeMillis())
    println("Current time: $cur")
    println("Input start time, such as \"11:00\"")

    val timeInput = readLine()!!
    val time = format.parse(cur.split(" ")[0] + " " + timeInput)

    delay(Duration.ofMillis(currentTimeMillis()%1000))

    println("Set starting time as $time")
    val startInMill = time.time - currentTimeMillis()
    if(startInMill < 2000){
        error("Wrong Input Time!")
    }
    var countDown = ((startInMill)/1000)*1000

    println("[Count Down] Deliver Task in $countDown ms")
    var lastTime = currentTimeMillis()

    while (true){
        if(countDown > 1000) {
            delay(Duration.ofMillis(1000))
        }else{
            delay(Duration.ofMillis(100))
        }
        countDown -= (currentTimeMillis() - lastTime)
        lastTime = currentTimeMillis()
        println("Current Time: " + cdf.format(currentTimeMillis()) + ", start in " + countDown + " ms")
        if(countDown <= 0){
            delay(Duration.ofMillis(startInMill - currentTimeMillis()))
            println("Task Delivered")
            break
        }
    }
    startTask(comboToken)
}


suspend fun startTask(combo: String){

    println(cdf.format(currentTimeMillis()) + " Spammer start")
    repeat(100){
        if(!success) {
            try {
                task(combo)
                delay(Duration.ofMillis(2000))
            } catch (e: Exception) {
                e.printStackTrace()
                delay(Duration.ofMillis(100))
            }
        }
    }
}


@OptIn(ExperimentalStdlibApi::class)
val headers = buildMap<String,String> {
   """
       x-rpc-client_type: 2
       x-rpc-app_version: 1.0.0
       x-rpc-sys_version: 6.0.1
       x-rpc-channel: mihoyo
       x-rpc-device_id: ca19401b-a987-3232-8763-c4d8d9b61a37
       x-rpc-device_name: Netease MuMu
       x-rpc-device_model: MuMu
       x-rpc-app_id: 1953439974
       Referer: https://app.mihoyo.com
       Host: api-cloudgame.mihoyo.com
       Connection: Keep-Alive
       Accept-Encoding: gzip
       User-Agent: okhttp/3.14.9
   """.trimIndent().split("\n").forEach {
       put(it.substringBefore(":").trim(),it.substringAfter(":").trim())
   }
}


val API = "https://api-cloudgame.mihoyo.com:443/hk4e_cg_cn/"
var success = false

@Serializable
data class CloudGenshinResp(
     val message:String,
     val retcode:String
)

suspend fun task(combo:String){
    val c = MockChromeClient()
    c.addIntrinsic{
        it.headers(headers)
        it.header("x-rpc-combo_token",combo)
    }
    val exe = c.post(API + "gamer/api/login")

    if(exe.statusCode() == 409){
        println(cdf.format(currentTimeMillis()) + exe.statusMessage())
    }else {
        val resp = exe.decode<CloudGenshinResp>()
        println(cdf.format(currentTimeMillis()) + resp)
        if (resp.retcode == "0") {
            println("Success, resp=0")
            if (!success) {
                success = true
                val vd = c.post(API + "gamer/api/onceActiveDone").body()
                println("OnceActiveDoneResp:$vd")
            }
        }
    }
}


