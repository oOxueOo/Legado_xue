package io.legado.app.lib.prefs

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemIconPreferenceBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.getCompatDrawable
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding


class IconListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {
    private var iconNames: Array<CharSequence>
    private val mEntryDrawables = arrayListOf<Drawable?>()

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.view_icon

        // 缩略图和显示名都从 entryValues（@array/icons）推导，
        // 不再单独维护一份 icon_names 数组，避免两处不同步。
        val values = entryValues
        if (!values.isNullOrEmpty()) {
            iconNames = values
        } else {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.IconListPreference, 0, 0)
            try {
                iconNames = a.getTextArray(R.styleable.IconListPreference_icons) ?: emptyArray()
            } finally {
                a.recycle()
            }
        }

        val labels = Array<CharSequence>(iconNames.size) { labelOf(iconNames[it].toString()) }
        if (entries.isNullOrEmpty() || entries.size != labels.size) {
            entries = labels
        }

        for (iconName in iconNames) {
            val resId = context.resources
                .getIdentifier(iconName.toString(), "mipmap", context.packageName)
            var d: Drawable? = null
            kotlin.runCatching {
                d = context.getCompatDrawable(resId)
            }
            mEntryDrawables.add(d)
        }
    }

    /** ic_launcher → iconMain；launcher1 → icon1 */
    private fun labelOf(value: String): String {
        if (value == "ic_launcher") return "iconMain"
        val suffix = value.removePrefix("launcher")
        return if (suffix.isEmpty()) value else "icon$suffix"
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val v = Preference.bindView<ImageView>(
            context,
            holder,
            icon,
            title,
            summary,
            widgetLayoutResource,
            R.id.preview,
            50,
            50
        )
        if (v is ImageView) {
            val selectedIndex = findIndexOfValue(value)
            if (selectedIndex >= 0) {
                mEntryDrawables.getOrNull(selectedIndex)?.let { v.setImageDrawable(it) }
            }
        }
    }

    override fun onClick() {
        getActivity()?.let {
            val dialog = IconDialog().apply {
                val args = Bundle()
                args.putString("value", value)
                args.putCharSequenceArray("entries", entries)
                args.putCharSequenceArray("entryValues", entryValues)
                args.putCharSequenceArray("iconNames", iconNames)
                arguments = args
                onChanged = { value ->
                    this@IconListPreference.value = value
                }
            }
            it.supportFragmentManager
                .beginTransaction()
                .add(dialog, getFragmentTag())
                .commitAllowingStateLoss()
        }
    }

    override fun onAttached() {
        super.onAttached()
        val fragment =
            getActivity()?.supportFragmentManager?.findFragmentByTag(getFragmentTag()) as IconDialog?
        fragment?.onChanged = { value ->
            this@IconListPreference.value = value
        }
    }

    private fun getActivity(): FragmentActivity? {
        val context = context
        if (context is FragmentActivity) {
            return context
        } else if (context is ContextWrapper) {
            val baseContext = context.baseContext
            if (baseContext is FragmentActivity) {
                return baseContext
            }
        }
        return null
    }

    private fun getFragmentTag(): String {
        return "icon_$key"
    }

    class IconDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

        var onChanged: ((value: String) -> Unit)? = null
        var dialogValue: String? = null
        var dialogEntries: Array<CharSequence>? = null
        var dialogEntryValues: Array<CharSequence>? = null
        var dialogIconNames: Array<CharSequence>? = null
        private val binding by viewBinding(DialogRecyclerViewBinding::bind)

        override fun onStart() {
            super.onStart()
            setLayout(0.8f, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
            binding.toolBar.setBackgroundColor(primaryColor)
            binding.toolBar.setTitle(R.string.change_icon)
            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            val adapter = Adapter(requireContext())
            binding.recyclerView.adapter = adapter
            arguments?.let {
                dialogValue = it.getString("value")
                dialogEntries = it.getCharSequenceArray("entries")
                dialogEntryValues = it.getCharSequenceArray("entryValues")
                dialogIconNames = it.getCharSequenceArray("iconNames")
                dialogEntryValues?.let { values ->
                    adapter.setItems(values.toList())
                }
            }
        }


        inner class Adapter(context: Context) :
            RecyclerAdapter<CharSequence, ItemIconPreferenceBinding>(context) {

            override fun getViewBinding(parent: ViewGroup): ItemIconPreferenceBinding {
                return ItemIconPreferenceBinding.inflate(inflater, parent, false)
            }

            override fun convert(
                holder: ItemViewHolder,
                binding: ItemIconPreferenceBinding,
                item: CharSequence,
                payloads: MutableList<Any>
            ) {
                binding.run {
                    // entries / entryValues / iconNames 三个数组长度由资源保证一致，
                    // 但后续增删图标仍可能对不上，这里统一按下标兜底，避免越界崩溃
                    val index = findIndexOfValue(item.toString())
                    dialogEntries?.getOrNull(index)?.let {
                        label.text = it
                    }
                    dialogIconNames?.getOrNull(index)?.let {
                        val resId = context.resources
                            .getIdentifier(it.toString(), "mipmap", context.packageName)
                        val d = try {
                            context.getCompatDrawable(resId)
                        } catch (e: Exception) {
                            null
                        }
                        d?.let {
                            icon.setImageDrawable(d)
                        }
                    }
                    label.isChecked = item.toString() == dialogValue
                    root.setOnClickListener {
                        onChanged?.invoke(item.toString())
                        this@IconDialog.dismissAllowingStateLoss()
                    }
                }
            }

            override fun registerListener(
                holder: ItemViewHolder,
                binding: ItemIconPreferenceBinding
            ) {
                holder.itemView.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        onChanged?.invoke(it.toString())
                    }
                }
            }

            private fun findIndexOfValue(value: String?): Int {
                dialogEntryValues?.let { values ->
                    for (i in values.indices.reversed()) {
                        if (values[i] == value) {
                            return i
                        }
                    }
                }
                return -1
            }
        }
    }
}
