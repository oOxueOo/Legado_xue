package io.legado.app.ui.main.bookshelf

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getCheckedIndex
import io.legado.app.utils.postEvent
import io.legado.app.utils.readText
import io.legado.app.help.ExportResultHandler
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi

/**
 * 书架Fragment基类
 * 处理书架界面相关的逻辑，包括菜单操作、书籍导入导出等功能
 */
abstract class BaseBookshelfFragment(layoutId: Int) :
    VMBaseFragment<BookshelfViewModel>(layoutId),
    MainFragmentInterface {

    override val position: Int? get() = arguments?.getInt("position")

    val activityViewModel by activityViewModels<MainViewModel>()
    override val viewModel by viewModels<BookshelfViewModel>()

    /** 导入书单的ActivityResultLauncher，支持从文件选择器选择文件 */
    private val importBookshelf = registerForActivityResult(HandleFileContract()) {
        kotlin.runCatching {
            it.uri?.readText(requireContext())?.let { text ->
                viewModel.importBookshelf(text, groupId)
            }
        }.onFailure {
            toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    /** 导出书单结果的ActivityResultLauncher，用于选择保存位置 */
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        ExportResultHandler.handleExportResult(requireActivity() as androidx.appcompat.app.AppCompatActivity, it, { text ->
            requireContext().sendToClip(text)
        }, null)
    }

    /** 当前分组ID，用于确定导入书籍的分组 */
    abstract val groupId: Long

    /** 当前书架的书籍列表 */
    abstract val books: List<Book>

    /** 是否只更新已读书籍的目录 */
    abstract var onlyUpdateRead: Boolean

    /** 分组LiveData观察者 */
    private var groupsLiveData: LiveData<List<BookGroup>>? = null

    /** 添加书籍时的等待对话框 */
    private val waitDialog by lazy {
        WaitDialog(requireContext()).apply {
            setOnCancelListener {
                viewModel.addBookJob?.cancel()
            }
        }
    }

    abstract fun gotoTop()

    /** 创建选项菜单，加载main_bookshelf菜单资源 */
    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
    }

    /** 处理选项菜单的点击事件 */
    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_remote -> startActivity<RemoteBookActivity>()
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_update_toc -> activityViewModel.upToc(books, onlyUpdateRead)
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_tag_manage -> startActivity<BookshelfTagManageActivity> {
                putExtra("groupId", groupId)
            }
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_add_url -> showAddBookByUrlAlert()
            R.id.menu_bookshelf_manage -> startActivity<BookshelfManageActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_download -> startActivity<CacheActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_export_bookshelf -> viewModel.exportBookshelf(books) { file ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData =
                        HandleFileContract.FileData("bookshelf.json", file, "application/json")
                }
            }

            R.id.menu_import_bookshelf -> importBookshelfAlert(groupId)
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
    }

    /** 初始化书籍分组数据观察 */
    protected fun initBookGroupData() {
        groupsLiveData?.removeObservers(viewLifecycleOwner)
        groupsLiveData = appDb.bookGroupDao.show.apply {
            observe(viewLifecycleOwner) {
                upGroup(it)
            }
        }
    }

    abstract fun upGroup(data: List<BookGroup>)

    abstract fun upSort()

    /** 观察LiveBus事件，处理添加书籍进度更新 */
    override fun observeLiveBus() {
        viewModel.addBookProgressLiveData.observe(this) { count ->
            if (count < 0) {
                waitDialog.dismiss()
            } else {
                waitDialog.setText("添加中... ($count)")
            }
        }
    }

    /** 显示通过URL添加书籍的对话框 */
    @SuppressLint("InflateParams")
    fun showAddBookByUrlAlert() {
        alert(titleResource = R.string.add_book_url) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    waitDialog.setText("添加中...")
                    waitDialog.show()
                    viewModel.addBookByUrl(it)
                }
            }
            cancelButton()
        }
    }

    /** 显示书架布局配置对话框 */
    @SuppressLint("InflateParams")
    fun configBookshelf() {
        alert(titleResource = R.string.bookshelf_layout) {
            var bookshelfSort = AppConfig.bookshelfSort
            var showBookname = AppConfig.showBookname
            var bookLayout = AppConfig.bookLayout
            var folderLayout = AppConfig.folderLayout
            var bookGroupStyle = AppConfig.bookGroupStyle
            var coverStyle = AppConfig.bookshelfCoverStyle
            val groupStyles = resources.getStringArray(R.array.group_style)
            val bookViews = resources.getStringArray(R.array.book_view_dialog)
            // 文件夹视图没有「紧凑列表」这一档：folderLayout 0 和 1 在渲染时走同一分支，显示完全一样
            val folderViews = resources.getStringArray(R.array.folder_view_dialog)
            val coverStyles = resources.getStringArray(R.array.cover_style)
            val bookNameStyles = resources.getStringArray(R.array.book_name_style)
            val sortStyles = resources.getStringArray(R.array.bookshelf_sort_style)
            // 弹窗显示顺序 → 存储值：书籍视图（列表 / 网格二~六 / 紧凑列表）
            val bookViewValues = intArrayOf(0, 2, 3, 4, 5, 6, 1)
            val folderViewValues = intArrayOf(0, 2, 3, 4, 5, 6)
            // 排序（按书名 / 按作者 / 手动排序 / 综合排序 / 按阅读时间 / 按更新时间）
            val sortValues = intArrayOf(2, 5, 3, 4, 0, 1)
            if (bookGroupStyle !in groupStyles.indices) {
                bookGroupStyle = 0
                AppConfig.bookGroupStyle = 0
            }
            if (bookLayout !in bookViews.indices) {
                bookLayout = 0
                AppConfig.bookLayout = 0
            }
            if (folderLayout !in folderViewValues) {
                folderLayout = 0
                AppConfig.folderLayout = 0
            }
            if (showBookname !in bookNameStyles.indices) {
                showBookname = 0
                AppConfig.showBookname = 0
            }
            if (coverStyle !in coverStyles.indices) {
                coverStyle = 0
                AppConfig.bookshelfCoverStyle = 0
            }
            if (bookshelfSort !in sortStyles.indices) {
                bookshelfSort = 0
                AppConfig.bookshelfSort = 0
            }
            val alertBinding =
                DialogBookshelfConfigBinding.inflate(layoutInflater)
                    .apply {
                        // ---------- 第一段：取值行 ----------
                        // 分组样式：标签(0) / 文件夹(1)，切到文件夹时下面紧跟出现「文件夹视图」
                        llFolderView.visibility =
                            if (bookGroupStyle == 1) View.VISIBLE else View.GONE
                        swDropdownSelectGroup.visibility =
                            if (bookGroupStyle == 0) View.VISIBLE else View.GONE
                        bindValueRow(llGroupStyle, tvGroupStyle, R.string.group_style, groupStyles, intArrayOf(0, 1), { bookGroupStyle }) { which ->
                            bookGroupStyle = which
                            llFolderView.visibility =
                                if (which == 1) View.VISIBLE else View.GONE
                            swDropdownSelectGroup.visibility =
                                if (which == 0) View.VISIBLE else View.GONE
                        }
                        bindValueRow(llFolderView, tvFolderView, R.string.folder_view, folderViews, folderViewValues, { folderLayout }) { which ->
                            folderLayout = which
                        }
                        bindValueRow(llBookView, tvBookView, R.string.book_view, bookViews, bookViewValues, { bookLayout }) { which ->
                            bookLayout = which
                            // 书名只在网格视图有意义；紧凑列表只有 48dp 封面，立体封面强制「无」
                            bookNameChoice.visibility =
                                if (which > 1) View.VISIBLE else View.GONE
                            bookCoverStyleChoice.visibility =
                                if (which == 1) View.GONE else View.VISIBLE
                            llShowMoreInfo.visibility =
                                if (which == 0) View.VISIBLE else View.GONE
                            swShowBookBorder.visibility =
                                if (which <= 1) View.VISIBLE else View.GONE
                        }
                        bindValueRow(bookCoverStyleChoice, tvCoverStyle, R.string.book_cover_style, coverStyles, intArrayOf(0, 1, 2, 3), { coverStyle }) { which ->
                            coverStyle = which
                        }
                        bindValueRow(bookNameChoice, tvBookName, R.string.book_name, bookNameStyles, intArrayOf(0, 1, 2), { showBookname }) { which ->
                            showBookname = which
                        }
                        bindValueRow(llSortRow, tvSort, R.string.sort, sortStyles, sortValues, { bookshelfSort }) { which ->
                            bookshelfSort = which
                        }
                        // ---------- 第二段：开关 ----------
                        swDropdownSelectGroup.isChecked = AppConfig.dropdownSelectGroup
                        swShowUnread.isChecked = AppConfig.showUnread
                        swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
                        swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
                        swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller
                        swShowBookshelfTagBar.isChecked = AppConfig.showBookshelfTagBar
                        // 初始化"显示更多信息"相关开关状态
                        llShowMoreInfo.visibility = if (bookLayout == 0) View.VISIBLE else View.GONE
                        swShowMoreInfo.isChecked = AppConfig.showMoreInfoInList
                        swShowIntro.isChecked = AppConfig.showIntroInList
                        swShowCategoryInfo.isChecked = AppConfig.showCategoryInfoInList
                        swShowReadProgress.isChecked = AppConfig.showBookshelfReadProgress
                        // 书籍外边框开关（仅在列表/紧凑列表视图时显示，默认关闭）
                        swShowBookBorder.visibility =
                            if (bookLayout <= 1) View.VISIBLE else View.GONE
                        swShowBookBorder.isChecked = AppConfig.showBookBorder
                        // 子菜单可见性
                        swShowIntro.visibility =
                            if (AppConfig.showMoreInfoInList) View.VISIBLE else View.GONE
                        swShowCategoryInfo.visibility =
                            if (AppConfig.showMoreInfoInList) View.VISIBLE else View.GONE
                        swShowReadProgress.visibility =
                            if (AppConfig.showMoreInfoInList) View.VISIBLE else View.GONE
                        // 简介行数选择器可见性（仅在显示简介勾选时显示）
                        llIntroLines.visibility =
                            if (AppConfig.showMoreInfoInList && AppConfig.showIntroInList) View.VISIBLE else View.GONE
                        // 更新简介行数显示文本
                        tvIntroLines.text = getString(R.string.intro_lines_value, AppConfig.introLinesInList)
                        // 监听"显示更多信息"开关变化
                        swShowMoreInfo.setOnCheckedChangeListener { _, isChecked ->
                            swShowIntro.visibility = if (isChecked) View.VISIBLE else View.GONE
                            swShowCategoryInfo.visibility = if (isChecked) View.VISIBLE else View.GONE
                            swShowReadProgress.visibility = if (isChecked) View.VISIBLE else View.GONE
                            // 更新简介行数选择器可见性
                            llIntroLines.visibility =
                                if (isChecked && swShowIntro.isChecked) View.VISIBLE else View.GONE
                        }
                        // 监听"显示简介"开关变化
                        swShowIntro.setOnCheckedChangeListener { _, isChecked ->
                            llIntroLines.visibility =
                                if (isChecked && swShowMoreInfo.isChecked) View.VISIBLE else View.GONE
                        }
                        // 点击简介行数选择器，弹出 NumberPickerDialog
                        llIntroLines.setOnClickListener {
                            NumberPickerDialog(requireContext())
                                .setTitle(getString(R.string.intro_lines))
                                .setMinValue(1)
                                .setMaxValue(10)
                                .setValue(AppConfig.introLinesInList)
                                .show { newValue ->
                                    AppConfig.introLinesInList = newValue
                                    tvIntroLines.text = getString(R.string.intro_lines_value, newValue)
                                    // 立即刷新书架以应用简介行数变化
                                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                                }
                        }
                        // 根据书籍视图控制书名 / 立体封面的可见性
                        bookNameChoice.visibility =
                            if (bookLayout > 1) View.VISIBLE else View.GONE
                        bookCoverStyleChoice.visibility =
                            if (bookLayout == 1) View.GONE else View.VISIBLE
                        // 音频视频横版封面（只影响内置音频/视频两组，默认关）
                        swMediaWideCover.isChecked = AppConfig.mediaWideCover
                        // ---------- 间距 ----------
                        margin.progress = AppConfig.bookshelfMargin
                    }
            customView { alertBinding.root }
            okButton {
                alertBinding.apply {
                    var recreate = false
                    var refreshBookshelf = false
                    if (AppConfig.bookGroupStyle != bookGroupStyle) {
                        AppConfig.bookGroupStyle = bookGroupStyle
                        recreate = true // 分组样式改变需要重新创建Activity
                    }
                    if (AppConfig.bookLayout != bookLayout) {
                        AppConfig.bookLayout = bookLayout
                        refreshBookshelf = true
                    }
                    if (AppConfig.folderLayout != folderLayout) {
                        AppConfig.folderLayout = folderLayout
                        refreshBookshelf = true
                    }
                    if (AppConfig.showBookname != showBookname) {
                        AppConfig.showBookname = showBookname
                        // 书架列表已 Compose 化，条目读取走 BOOKSHELF_REFRESH 重新加载的显示配置，
                        // 不再需要整页重建
                        refreshBookshelf = true
                    }
                    if (AppConfig.mediaWideCover != swMediaWideCover.isChecked) {
                        // 只换布局与取图尺寸，不碰封面缓存：切回来不必整库重下
                        AppConfig.mediaWideCover = swMediaWideCover.isChecked
                        refreshBookshelf = true
                    }
                    if (AppConfig.bookshelfMargin != margin.progress) {
                        AppConfig.bookshelfMargin = margin.progress
                        refreshBookshelf = true
                    }
                    if (AppConfig.showUnread != swShowUnread.isChecked) {
                        AppConfig.showUnread = swShowUnread.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showLastUpdateTime != swShowLastUpdateTime.isChecked) {
                        AppConfig.showLastUpdateTime = swShowLastUpdateTime.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showWaitUpCount != swShowWaitUpBooks.isChecked) {
                        AppConfig.showWaitUpCount = swShowWaitUpBooks.isChecked
                        activityViewModel.postUpBooksLiveData(true)
                    }
                    if (AppConfig.showBookshelfFastScroller != swShowBookshelfFastScroller.isChecked) {
                        AppConfig.showBookshelfFastScroller = swShowBookshelfFastScroller.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showBookshelfTagBar != swShowBookshelfTagBar.isChecked) {
                        AppConfig.showBookshelfTagBar = swShowBookshelfTagBar.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    // 立体封面档位：只影响封面绘制，不改条目尺寸，刷新一次即可
                    if (AppConfig.bookshelfCoverStyle != coverStyle) {
                        AppConfig.bookshelfCoverStyle = coverStyle
                        refreshBookshelf = true
                    }
                    // 保存"显示更多信息"相关配置
                    if (AppConfig.showMoreInfoInList != swShowMoreInfo.isChecked) {
                        AppConfig.showMoreInfoInList = swShowMoreInfo.isChecked
                        refreshBookshelf = true
                    }
                    if (AppConfig.showIntroInList != swShowIntro.isChecked) {
                        AppConfig.showIntroInList = swShowIntro.isChecked
                        refreshBookshelf = true
                    }
                    if (AppConfig.showCategoryInfoInList != swShowCategoryInfo.isChecked) {
                        AppConfig.showCategoryInfoInList = swShowCategoryInfo.isChecked
                        refreshBookshelf = true
                    }
                    if (AppConfig.showBookshelfReadProgress != swShowReadProgress.isChecked) {
                        AppConfig.showBookshelfReadProgress = swShowReadProgress.isChecked
                        refreshBookshelf = true
                    }
                    // 简介行数已在 NumberPickerDialog 回调中保存，无需在此处保存
                    // 保存"书籍外边框"开关配置
                    if (AppConfig.showBookBorder != swShowBookBorder.isChecked) {
                        AppConfig.showBookBorder = swShowBookBorder.isChecked
                        refreshBookshelf = true
                    }
                    // 保存"下拉选择分组"开关配置
                    if (AppConfig.dropdownSelectGroup != swDropdownSelectGroup.isChecked) {
                        AppConfig.dropdownSelectGroup = swDropdownSelectGroup.isChecked
                        recreate = true // 下拉选择分组改变需要重新创建Activity以更新标题栏行为
                    }
                    if (AppConfig.bookshelfSort != bookshelfSort) {
                        AppConfig.bookshelfSort = bookshelfSort
                        upSort()
                    }
                    if (recreate) {
                        postEvent(EventBus.RECREATE, "")
                    } else if (refreshBookshelf) {
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                }
            }
            cancelButton()
        }
    }

    /**
     * 取值行：左侧标题 + 右侧当前值，点右侧弹出单选列表，选完即更新文字，
     * 「确定」时统一写回配置。
     * values：弹窗显示序号 → 实际存储值，用于「显示顺序」和「存储语义」不一致的行。
     */
    private fun bindValueRow(
        row: View,
        valueView: TextView,
        titleRes: Int,
        items: Array<String>,
        values: IntArray,
        getValue: () -> Int,
        setValue: (Int) -> Unit
    ) {
        fun displayIndex(): Int =
            values.indexOfFirst { it == getValue() }.takeIf { it >= 0 } ?: 0
        valueView.text = items[displayIndex()]
        row.setOnClickListener {
            val checked = displayIndex()
            alert(titleResource = titleRes) {
                singleChoiceItems(items, checked) { dialog, which ->
                    dialog.dismiss()
                    setValue(values[which])
                    valueView.text = items[which]
                }
                cancelButton()
            }
        }
    }

    /** 显示导入书单对话框，支持输入URL或选择本地文件 */
    private fun importBookshelfAlert(groupId: Long) {
        alert(titleResource = R.string.import_bookshelf) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url/json"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.importBookshelf(it, groupId)
                }
            }
            cancelButton()
            neutralButton(R.string.select_file) {
                importBookshelf.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = arrayOf("txt", "json")
                }
            }
        }
    }
}
