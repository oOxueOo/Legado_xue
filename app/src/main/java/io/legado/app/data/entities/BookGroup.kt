package io.legado.app.data.entities

import android.content.Context
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.help.config.AppConfig
import kotlinx.parcelize.Parcelize

@Suppress("ConstPropertyName")
@Parcelize
@Entity(tableName = "book_groups")
data class BookGroup(
    @PrimaryKey
    val groupId: Long = 0b1,
    var groupName: String = "",
    var cover: String? = null,
    var order: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var enableRefresh: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    var show: Boolean = true,
    @ColumnInfo(defaultValue = "-1")
    var bookSort: Int = -1,
    // 只更新已读
    @ColumnInfo(defaultValue = "0")
    var onlyUpdateRead: Boolean = false
) : Parcelable {

    companion object {
        const val IdRoot = -100L
        const val IdAll = -1L
        const val IdLocal = -2L
        const val IdAudio = -3L
        const val IdVideo = -6L

        /** 小说：筛 text 类型位 */
        const val IdText = -7L

        /** 漫画：筛 image 类型位 */
        const val IdImage = -8L

        /** 6 个基础筛选分组：只有「显示」开关，不可改名、改封面、删除，但可自由排序 */
        val baseGroupIds: LongArray = longArrayOf(IdAll, IdText, IdLocal, IdAudio, IdVideo, IdImage)

        fun isBaseGroup(groupId: Long): Boolean = groupId in baseGroupIds
    }

    /**
     * 分组管理与标签栏的展示名：基础分组直接用自己的名字（全部 / 小说 / 本地 / 音频 / 视频 / 漫画），
     * 不再拼成「全部(全部)」这种重复括号。
     */
    fun getManageName(@Suppress("UNUSED_PARAMETER") context: Context): String = groupName

    fun getRealBookSort(): Int {
        if (bookSort < 0) {
            return AppConfig.bookshelfSort
        }
        return bookSort
    }

    override fun hashCode(): Int {
        return groupId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is BookGroup) {
            return other.groupId == groupId
                    && other.groupName == groupName
                    && other.cover == cover
                    && other.bookSort == bookSort
                    && other.enableRefresh == enableRefresh
                    && other.onlyUpdateRead == onlyUpdateRead
                    && other.show == show
                    && other.order == order
        }
        return false
    }

}