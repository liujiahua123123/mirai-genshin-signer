package net.mamoe.n

import kotlinx.html.currentTimeMillis
import kotlinx.serialization.encodeToString
import org.jsoup.Connection
import java.security.MessageDigest
import java.util.*

object AccountIdCookie:StringComponentKey
object AccountTokenCookie:StringComponentKey


object DeviceId:StringComponentKey
object Roles:ComponentKey<List<GetGameRolesResponse.Companion.Role>>
object TaskResults:ComponentKey<MutableList<TaskResult>>

data class TaskResult(
    val uid:String,
    val name:String,
    val level:Int,
    val server:String,
    val message:String,
){
    override fun toString():String{
        return buildString {
            append(name)
            append("[Lv.")
            append(level)
            append(" ")
            append(server)
            append("]")
            append(": ")
            append(message)
        }
    }
}

object InitClient:Step{
    override val name: String
        get() = "Init Client"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            client.addCookie("api-takumi.mihoyo.com","account_id",component[AccountIdCookie])
            client.addCookie("api-takumi.mihoyo.com","cookie_token",component[AccountTokenCookie])
            component[DeviceId] = UUID.nameUUIDFromBytes((component[AccountIdCookie] + component[AccountTokenCookie]).toByteArray()).toString().replace("-","").uppercase()
        }
}

object GetRoles:Step{
    override val name: String
        get() = "Get Roles"

    @OptIn(ExperimentalStdlibApi::class)
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            val r = client
                .get("https://api-takumi.mihoyo.com/binding/api/getUserGameRolesByCookie?game_biz=hk4e_cn")
                .decode<GetGameRolesResponse>()
            component[Roles] = r.data.list
        }
}

object FilterRoles:Step{
    override val name: String
        get() = "Filter Roles"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            component[TaskResults] = mutableListOf()
            component[Roles].forEach {role ->
                val r = client.get("https://api-takumi.mihoyo.com/event/bbs_sign_reward/info"){
                    data(SignRewordInfoRequest(
                        uid = role.game_uid,
                        region = role.region
                    ))
                }.decode<SignRewordInfoResponse>()

                if(r.data.first_bind){
                    component[TaskResults].add(TaskResult(
                        role.game_uid,role.nickname,role.level,role.region_name,"第一次签到需要手动绑定"
                    ))
                }else{
                    if(r.data.is_sign){
                        component[TaskResults].add(TaskResult(
                            role.game_uid,role.nickname,role.level,role.region_name,"今日已完成签到"
                        ))
                    }
                }
            }
        }
}

object DoSigns:Step{
    override val name: String
        get() = "Do Signs"
    override val process: suspend StepExecutor.() -> Unit
        get() = {
            component[Roles].forEach {role ->
                if(!component[TaskResults].any { it.uid == role.game_uid }){
                    val r = client.post("https://api-takumi.mihoyo.com/event/bbs_sign_reward/sign"){
                        requestBody(
                            ksoupJson.encodeToString(
                                SignRequest(
                                    uid = role.game_uid,
                                    region = role.region
                                )
                            ))
                        val ds = getDS()
                        header("x-rpc-device_id",component[DeviceId])
                        header("x-rpc-client_type",ds.clientType)
                        header("x-rpc-app_version",ds.clientVersion)
                        header("DS",ds.ds)
                        header("Referer","https://webstatic.mihoyo.com/bbs/event/signin-ys/index.html?bbs_auth_required=true&act_id=${SIGN_ACT_ID}&utm_source=bbs&utm_medium=mys&utm_campaign=icon")
                        header("Origin","https://webstatic.mihoyo.com")
                    }.decode<SignResponse>()
                    when(r.retcode){
                        -10001 -> {
                            component[TaskResults].add(
                                TaskResult(
                                    role.game_uid, role.nickname, role.level, role.region_name, "DS失败:" + r.message
                                )
                            )
                        }
                        else -> {
                            component[TaskResults].add(TaskResult(
                                role.game_uid,role.nickname,role.level,role.region_name,r.message
                            ))
                        }
                    }
                }
            }
        }

    data class DSVerify(
        val ds:String,
        val clientType:String,
        val clientVersion:String
    )

    fun getDS(type:Int = 5, time:Int = (currentTimeMillis() /1000).toInt(), random:String = buildString { repeat(6){append("abcdefghijklmnopqrstuvwxyz1234567890".random())} }):DSVerify{
        /**
        # 1:  ios
        # 2:  android
        # 4:  pc web
        # 5:  mobile web
         */
        val salt = when(type){
            5 -> Pair("h8w582wxwgqvahcdkpvdhbh2w9casgfl","2.3.0")
            2 -> Pair("dmq2p7ka6nsu0d3ev6nex4k1ndzrnfiy","2.8.0")
            else -> {
                throw IllegalArgumentException("Type $type for calulcate DS not supported")
            }
        }

        return DSVerify(
            ds = buildString {
                append(time)
                append(",")
                append(random)
                append(",")
                append("salt=${salt.first}&t=${time}&r=${random}".md5()) },
            clientType = type.toString(),
            clientVersion = salt.second)
    }

    fun String.md5(): String = toByteArray().md5()


    @JvmOverloads
    fun ByteArray.md5(offset: Int = 0, length: Int = size - offset): String {
        return MessageDigest.getInstance("MD5").apply { update(this@md5, offset, length) }.digest().toHex()
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}




suspend fun doSigns(accountId:String, accountToken:String){

    val client = MockChromeClient().apply {
        addIntrinsic {
            it.networkRetry(8)
            it.header("Origin","https://webstatic.mihoyo.com")
        }
    }

    val provider = object:ProxyProvider{
        override fun invoke(): Pair<(Connection) -> Unit, String> {
            return Pair({},"localhost")
        }
    }

    val worker = WorkerImpl("MHY Signer",debugMode = false)
    val component = Component().apply {
        this[AccountIdCookie] = accountId
        this[AccountTokenCookie]  = accountToken
    }

    StepExecutor(
        worker,component, client,provider
    ).executeSteps(
        InitClient,GetRoles,FilterRoles,DoSigns
    )

    println("Sign Complete")
    component[TaskResults].forEach {
        println(it.toString())
    }
}


