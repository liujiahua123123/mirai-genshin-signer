package net.mamoe.n

import kotlinx.serialization.Serializable


/**
 * POST https://api-takumi.mihoyo.com/event/bbs_sign_reward/sign
 */
const val SIGN_ACT_ID = "e202009291139501"

@Serializable
data class SignRequest(
    val act_id:String = SIGN_ACT_ID,
    val ragion:String = "cn_gf01",
    val uid:String
):JsonRequestBody

@Serializable
data class SignResponse(
    //val data:Any? = null,
    val message:String,
    val retcode:Int,
)

/**
 * GET https://api-takumi.mihoyo.com/binding/api/getUserGameRolesByCookie?game_biz=hk4e_cn
 */

@Serializable
data class GetGameRolesResponse(
    val data: Data,
    val message: String,
    val retcode: Int
){
    companion object{
        @Serializable
        data class Data(
            val list: List<Role>
        )

        @Serializable
        data class Role(
            val game_biz: String,
            val game_uid: String,
            val is_chosen: Boolean,
            val is_official: Boolean,
            val level: Int,
            val nickname: String,
            val region: String,
            val region_name: String
        )
    }
}


/**
 * GET https://api-takumi.mihoyo.com/event/bbs_sign_reward/info
 */
@Serializable
data class SignRewordInfoRequest(
    val act_id:String = SIGN_ACT_ID,
    val ragion:String = "cn_gf01",
    val uid:String
):GetParams

@Serializable
data class SignRewordInfoResponse(
    val data: Data,
    val message: String,
    val retcode: Int
){
    companion object{
        @Serializable
        data class Data(
            val first_bind: Boolean,
            val is_sign: Boolean,
            val is_sub: Boolean,
            val month_first: Boolean,
            val today: String,
            val total_sign_day: Int
        )
    }
}




