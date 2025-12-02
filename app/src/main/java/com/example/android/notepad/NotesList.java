/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.content.ContentValues;

/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_NOTE, // 2 - 新增内容列用于搜索
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 3
            NotePad.Notes.COLUMN_NAME_CATEGORY_ID, // 4 - 新增分类ID
            NotePad.Notes.COLUMN_NAME_IS_TODO, // 5 - 新增：是否为待办
            NotePad.Notes.COLUMN_NAME_IS_COMPLETED, // 6 - 新增：是否完成
            NotePad.Notes.COLUMN_NAME_DUE_DATE, // 7 - 新增：截止日期
            NotePad.Notes.COLUMN_NAME_PRIORITY // 8 - 新增：优先级
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;

    /** The index of the note content column */
    private static final int COLUMN_INDEX_NOTE = 2;

    /** The index of the modification date column */
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 3;

    /** The index of the category ID column */
    private static final int COLUMN_INDEX_CATEGORY_ID = 4;

    /** The index of the is_todo column */
    private static final int COLUMN_INDEX_IS_TODO = 5;

    /** The index of the is_completed column */
    private static final int COLUMN_INDEX_IS_COMPLETED = 6;

    /** The index of the due_date column */
    private static final int COLUMN_INDEX_DUE_DATE = 7;

    /** The index of the priority column */
    private static final int COLUMN_INDEX_PRIORITY = 8;

    private SimpleCursorAdapter mAdapter;
    private LinearLayout mSearchLayout;
    private EditText mSearchEditText;
    private Button mSearchButton;
    private Button mClearButton;
    private String mCurrentSearchQuery = "";
    private long currentFilterCategoryId = -1; // -1 表示显示所有分类
    private String currentFilterCategoryName = "";
    private int currentTodoFilter = -1; // -1:全部, 0:仅笔记, 1:仅待办, 2:未完成待办

    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);

        // 初始化搜索界面
        initSearchView();

        // 执行初始查询（显示所有笔记）
        performQuery("");
    }

    /**
     * 初始化搜索界面
     */
    private void initSearchView() {
        // 加载搜索布局
        LayoutInflater inflater = LayoutInflater.from(this);
        mSearchLayout = (LinearLayout) inflater.inflate(R.layout.search_view, null);

        mSearchEditText = (EditText) mSearchLayout.findViewById(R.id.search_edit_text);
        mSearchButton = (Button) mSearchLayout.findViewById(R.id.search_button);
        mClearButton = (Button) mSearchLayout.findViewById(R.id.clear_button);

        // 设置搜索按钮点击事件
        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        // 设置清除按钮点击事件
        mClearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSearch();
            }
        });

        // 设置键盘搜索按钮事件
        mSearchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();
                    return true;
                }
                return false;
            }
        });

        // 将搜索布局添加到列表顶部
        getListView().addHeaderView(mSearchLayout);

        // 默认隐藏搜索布局
        mSearchLayout.setVisibility(View.GONE);
    }

    /**
     * 执行搜索
     */
    private void performSearch() {
        String query = mSearchEditText.getText().toString().trim();
        mCurrentSearchQuery = query;
        performQuery(query);
    }

    /**
     * 清除搜索
     */
    private void clearSearch() {
        mSearchEditText.setText("");
        mCurrentSearchQuery = "";
        performQuery("");
    }

    /**
     * 执行查询
     * @param query 搜索查询字符串
     */
    private void performQuery(String query) {
        String selection = null;
        String[] selectionArgs = null;

        List<String> selectionParts = new ArrayList<>();
        List<String> argsList = new ArrayList<>();

        // 搜索条件
        if (!TextUtils.isEmpty(query)) {
            selectionParts.add("(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
                    NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)");
            String searchPattern = "%" + query + "%";
            argsList.add(searchPattern);
            argsList.add(searchPattern);
        }

        // 分类筛选条件
        if (currentFilterCategoryId != -1) {
            selectionParts.add(NotePad.Notes.COLUMN_NAME_CATEGORY_ID + " = ?");
            argsList.add(String.valueOf(currentFilterCategoryId));
        }

        // 待办筛选条件
        if (currentTodoFilter != -1) {
            switch (currentTodoFilter) {
                case 0: // 仅笔记
                    selectionParts.add(NotePad.Notes.COLUMN_NAME_IS_TODO + " = 0");
                    break;
                case 1: // 仅待办
                    selectionParts.add(NotePad.Notes.COLUMN_NAME_IS_TODO + " = 1");
                    break;
                case 2: // 未完成待办
                    selectionParts.add(NotePad.Notes.COLUMN_NAME_IS_TODO + " = 1 AND " +
                            NotePad.Notes.COLUMN_NAME_IS_COMPLETED + " = 0");
                    break;
            }
        }

        if (!selectionParts.isEmpty()) {
            selection = TextUtils.join(" AND ", selectionParts);
            selectionArgs = argsList.toArray(new String[0]);
        }

        Cursor cursor = managedQuery(
                getIntent().getData(),
                PROJECTION,
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        setupAdapter(cursor);
    }

    /**
     * 设置适配器
     */
    private void setupAdapter(Cursor cursor) {
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };

        int[] viewIDs = {
                android.R.id.text1,    // 标题
                android.R.id.text2,    // 内容预览
                R.id.timestamp         // 时间戳
        };

        mAdapter = new SimpleCursorAdapter(
                this,
                R.layout.noteslist_item,
                cursor,
                dataColumns,
                viewIDs
        );

        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.timestamp) {
                    // 处理时间戳显示
                    TextView textView = (TextView) view;
                    long timestamp = cursor.getLong(columnIndex);
                    String formattedDate = formatDate(timestamp);
                    textView.setText(formattedDate);
                    return true;
                } else if (view.getId() == android.R.id.text2) {
                    // 处理内容预览显示
                    TextView textView = (TextView) view;
                    String noteContent = cursor.getString(columnIndex);
                    // 获取分类信息并显示
                    long categoryId = cursor.getLong(COLUMN_INDEX_CATEGORY_ID);
                    String categoryInfo = getCategoryInfo(categoryId);

                    // 获取待办状态
                    boolean isTodo = cursor.getInt(COLUMN_INDEX_IS_TODO) == 1;
                    boolean isCompleted = cursor.getInt(COLUMN_INDEX_IS_COMPLETED) == 1;

                    String preview = "";
                    if (!TextUtils.isEmpty(noteContent)) {
                        preview = noteContent.length() > 40 ?
                                noteContent.substring(0, 40) + "..." : noteContent;
                    }

                    String displayText = categoryInfo;
                    if (isTodo) {
                        displayText += " | 待办" + (isCompleted ? "✓" : "");
                    }
                    displayText += " | " + preview;

                    textView.setText(displayText);
                    return true;
                } else if (view.getId() == android.R.id.text1) {
                    // 处理标题显示
                    TextView textView = (TextView) view;
                    String title = cursor.getString(columnIndex);

                    // 获取待办状态
                    boolean isTodo = cursor.getInt(COLUMN_INDEX_IS_TODO) == 1;
                    boolean isCompleted = cursor.getInt(COLUMN_INDEX_IS_COMPLETED) == 1;

                    // 如果是已完成的待办，添加删除线
                    if (isTodo && isCompleted) {
                        textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                        textView.setAlpha(0.6f);
                    } else {
                        textView.setPaintFlags(textView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                        textView.setAlpha(1.0f);
                    }

                    textView.setText(highlightText(title, mCurrentSearchQuery));
                    return true;
                }
                return false;
            }
        });

        setListAdapter(mAdapter);
        updateTitle();
    }

    /**
     * 高亮显示搜索文本
     */
    private CharSequence highlightText(String text, String query) {
        if (TextUtils.isEmpty(query) || TextUtils.isEmpty(text)) {
            return text;
        }

        // 这里可以添加文本高亮逻辑
        // 简单实现：直接返回原文本
        return text;
    }

    /**
     * 格式化时间戳
     */
    private String formatDate(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * 获取分类信息
     */
    private String getCategoryInfo(long categoryId) {
        Cursor cursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories.COLUMN_NAME_CATEGORY_NAME},
                NotePad.Categories._ID + "=?",
                new String[]{String.valueOf(categoryId)},
                null
        );

        String categoryName = "未知分类";
        if (cursor != null && cursor.moveToFirst()) {
            categoryName = cursor.getString(0);
            cursor.close();
        }
        return categoryName;
    }

    /**
     * 更新标题显示搜索状态
     */
    private void updateTitle() {
        String baseTitle = getString(R.string.title_notes_list);
        StringBuilder titleBuilder = new StringBuilder();

        // 添加待办筛选状态
        switch (currentTodoFilter) {
            case 0:
                titleBuilder.append("笔记");
                break;
            case 1:
                titleBuilder.append("待办");
                break;
            case 2:
                titleBuilder.append("未完成待办");
                break;
            default:
                titleBuilder.append(baseTitle);
                break;
        }

        // 添加分类筛选状态
        if (currentFilterCategoryId != -1) {
            titleBuilder.append(" - ").append(currentFilterCategoryName);
        }

        // 添加搜索状态
        if (!TextUtils.isEmpty(mCurrentSearchQuery)) {
            titleBuilder.append(" - 搜索: ").append(mCurrentSearchQuery);
        }

        // 添加记录数量
        titleBuilder.append(" (").append(mAdapter.getCount()).append(" 条)");

        setTitle(titleBuilder.toString());
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 添加分类管理菜单项
        menu.add(Menu.NONE, 100, Menu.NONE, "分类管理")
                .setIcon(android.R.drawable.ic_menu_sort_by_size)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 添加新建待办菜单项
        menu.add(Menu.NONE, 101, Menu.NONE, "新建待办")
                .setIcon(android.R.drawable.ic_menu_agenda)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        // 添加分类筛选菜单项
        SubMenu filterSubMenu = menu.addSubMenu("按分类筛选");
        setupCategoryFilterMenu(filterSubMenu);

        // 添加待办筛选菜单项
        SubMenu todoFilterSubMenu = menu.addSubMenu("待办筛选");
        setupTodoFilterMenu(todoFilterSubMenu);

        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    /**
     * 设置分类筛选菜单
     */
    private void setupCategoryFilterMenu(SubMenu subMenu) {
        // 添加"全部"选项
        subMenu.add(Menu.NONE, 0, 0, "全部笔记")
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        currentFilterCategoryId = -1;
                        currentFilterCategoryName = "";
                        performQuery(mCurrentSearchQuery);
                        return true;
                    }
                });

        // 从数据库加载分类
        Cursor cursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{
                        NotePad.Categories._ID,
                        NotePad.Categories.COLUMN_NAME_CATEGORY_NAME
                },
                null, null, NotePad.Categories.COLUMN_NAME_CREATE_DATE + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                final long categoryId = cursor.getLong(0);
                final String categoryName = cursor.getString(1);

                subMenu.add(Menu.NONE, (int)categoryId, (int)categoryId, categoryName)
                        .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                currentFilterCategoryId = categoryId;
                                currentFilterCategoryName = categoryName;
                                performQuery(mCurrentSearchQuery);
                                return true;
                            }
                        });
            }
            cursor.close();
        }
    }

    /**
     * 设置待办筛选菜单
     */
    private void setupTodoFilterMenu(SubMenu subMenu) {
        subMenu.add(Menu.NONE, 200, 0, "全部")
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        currentTodoFilter = -1;
                        performQuery(mCurrentSearchQuery);
                        return true;
                    }
                });

        subMenu.add(Menu.NONE, 201, 1, "仅待办")
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        currentTodoFilter = 1;
                        performQuery(mCurrentSearchQuery);
                        return true;
                    }
                });

        subMenu.add(Menu.NONE, 202, 2, "仅笔记")
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        currentTodoFilter = 0;
                        performQuery(mCurrentSearchQuery);
                        return true;
                    }
                });

        subMenu.add(Menu.NONE, 203, 3, "未完成待办")
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        currentTodoFilter = 2;
                        performQuery(mCurrentSearchQuery);
                        return true;
                    }
                });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // The paste menu item is enabled if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // If the clipboard contains an item, enables the Paste option on the menu.
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // If the clipboard is empty, disables the menu's Paste option.
            mPasteItem.setEnabled(false);
        }

        // Gets the number of notes currently being displayed.
        final boolean haveItems = getListAdapter().getCount() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {

            // This is the selected item.
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            Intent[] specifics = new Intent[1];

            // Sets the Intent in the array to be an EDIT action on the URI of the selected note.
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // Creates an array of menu items with one element. This will contain the EDIT option.
            MenuItem[] items = new MenuItem[1];

            // Creates an Intent with no specific action, using the URI of the selected note.
            Intent intent = new Intent(null, uri);

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                    Menu.NONE,                  // A unique item ID is not required.
                    Menu.NONE,                  // The alternatives don't need to be in order.
                    null,                       // The caller's name is not excluded from the group.
                    specifics,                  // These specific options must appear first.
                    intent,                     // These Intent objects map to the options in specifics.
                    Menu.NONE,                  // No flags are required.
                    items                       // The menu items generated from the specifics-to-
                    // Intents mapping
            );
            // If the Edit menu item exists, adds shortcuts for it.
            if (items[0] != null) {

                // Sets the Edit menu item shortcut to numeric "1", letter "e"
                items[0].setShortcut('1', 'e');
            }
        } else {
            // If the list is empty, removes any existing alternative actions from the menu
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        // Displays the menu
        return true;
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     *
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            showCategoryDialogForNewNote();
            return true;
        } else if (id == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_search) {
            // 切换搜索布局的显示/隐藏
            if (mSearchLayout.getVisibility() == View.VISIBLE) {
                mSearchLayout.setVisibility(View.GONE);
                // 如果搜索布局隐藏，清除搜索
                if (!TextUtils.isEmpty(mCurrentSearchQuery)) {
                    clearSearch();
                }
            } else {
                mSearchLayout.setVisibility(View.VISIBLE);
                mSearchEditText.requestFocus();
            }
            return true;
        } else if (item.getItemId() == 100) {
            // 打开分类管理界面
            startActivity(new Intent(this, CategoryManager.class));
            return true;
        } else if (item.getItemId() == 101) {
            // 新建待办事项
            showCategoryDialogForNewTodo();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 显示分类选择对话框用于新建笔记
     */
    private void showCategoryDialogForNewNote() {
        CategorySelectDialog dialog = new CategorySelectDialog(this,
                new CategorySelectDialog.OnCategorySelectedListener() {
                    @Override
                    public void onCategorySelected(long categoryId, String categoryName) {
                        // 创建新笔记并设置分类
                        createNewNoteWithCategory(categoryId);
                    }
                });
        dialog.show();
    }

    /**
     * 显示分类选择对话框用于新建待办事项
     */
    private void showCategoryDialogForNewTodo() {
        CategorySelectDialog dialog = new CategorySelectDialog(this,
                new CategorySelectDialog.OnCategorySelectedListener() {
                    @Override
                    public void onCategorySelected(long categoryId, String categoryName) {
                        // 创建新待办事项并设置分类
                        createNewTodoWithCategory(categoryId);
                    }
                });
        dialog.show();
    }

    /**
     * 创建带分类的新笔记
     */
    private void createNewNoteWithCategory(long categoryId) {
        // 创建新笔记
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY_ID, categoryId);
        values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 0); // 标记为普通笔记

        // 添加必要的默认值
        Long now = Long.valueOf(System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, "新笔记");
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");

        // 在插入时直接传入包含分类ID的values
        Uri newNoteUri = getContentResolver().insert(getIntent().getData(), values);

        if (newNoteUri != null) {
            // 打开编辑器
            startActivity(new Intent(Intent.ACTION_EDIT, newNoteUri));
        } else {
            Log.e(TAG, "Failed to create new note");
            Toast.makeText(this, "创建笔记失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 创建带分类的新待办事项
     */
    private void createNewTodoWithCategory(long categoryId) {
        // 创建新待办事项
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY_ID, categoryId);
        values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 1); // 标记为待办事项
        values.put(NotePad.Notes.COLUMN_NAME_IS_COMPLETED, 0); // 默认未完成
        values.put(NotePad.Notes.COLUMN_NAME_PRIORITY, NotePad.Notes.PRIORITY_MEDIUM); // 默认中等优先级

        // 添加必要的默认值
        Long now = Long.valueOf(System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, "新待办事项");
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");

        // 在插入时直接传入包含分类ID的values
        Uri newTodoUri = getContentResolver().insert(getIntent().getData(), values);

        if (newTodoUri != null) {
            // 打开待办编辑器
            Intent intent = new Intent(this, TodoEditor.class);
            intent.setData(newTodoUri);
            startActivity(intent);
        } else {
            Log.e(TAG, "Failed to create new todo");
            Toast.makeText(this, "创建待办事项失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // 获取当前项是否为待办事项
        boolean isTodo = cursor.getInt(COLUMN_INDEX_IS_TODO) == 1;
        boolean isCompleted = cursor.getInt(COLUMN_INDEX_IS_COMPLETED) == 1;

        // 如果是待办事项，添加快捷操作
        if (isTodo) {
            if (isCompleted) {
                menu.add(Menu.NONE, R.id.context_mark_incomplete, 0, "标记为未完成");
            } else {
                menu.add(Menu.NONE, R.id.context_mark_complete, 0, "标记为完成");
            }
        }

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // Gets a handle to the clipboard service.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // Copies the notes URI to the clipboard. In effect, this copies the note itself
            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            // Returns to the caller and skips further processing.
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // Deletes the note from the provider by passing in a URI in note ID format.
            // Please see the introductory note about performing provider operations on the
            // UI thread.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );

            // Returns to the caller and skips further processing.
            return true;
        } else if (id == R.id.context_mark_complete || id == R.id.context_mark_incomplete) {
            // 切换待办完成状态
            toggleTodoCompletion(noteUri, id == R.id.context_mark_complete);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * 切换待办事项的完成状态
     */
    private void toggleTodoCompletion(Uri noteUri, boolean complete) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_IS_COMPLETED, complete ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        getContentResolver().update(
                noteUri,
                values,
                null,
                null
        );

        Toast.makeText(this, complete ? "标记为完成" : "标记为未完成", Toast.LENGTH_SHORT).show();

        // 刷新列表
        performQuery(mCurrentSearchQuery);
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     *
     * This method handles incoming actions of either PICK (get data from the provider) or
     * GET_CONTENT (get or create data). If the incoming action is EDIT, this method sends a
     * new Intent to start NoteEditor.
     * @param l The ListView that contains the clicked item
     * @param v The View of the individual item
     * @param position The position of v in the displayed list
     * @param id The row ID of the clicked item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // 需要考虑搜索头部的偏移
        int headerCount = l.getHeaderViewsCount();
        if (position < headerCount) {
            // 点击了头部视图（搜索栏），不执行任何操作
            return;
        }

        // 调整位置以考虑头部视图
        int adjustedPosition = position - headerCount;

        // 从适配器获取正确的ID
        Cursor cursor = (Cursor) getListAdapter().getItem(adjustedPosition);
        if (cursor == null) {
            Log.e(TAG, "Cursor is null at position: " + adjustedPosition);
            return;
        }

        long noteId = cursor.getLong(0); // _ID 列

        // 检查是否为待办事项
        boolean isTodo = cursor.getInt(COLUMN_INDEX_IS_TODO) == 1;

        // Constructs a new URI from the incoming URI and the row ID
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), noteId);

        // Gets the action from the incoming Intent
        String action = getIntent().getAction();

        // Handles requests for note data
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // Sets the result to return to the component that called this Activity. The
            // result contains the new URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            // 根据是否为待办事项打开不同的编辑器
            if (isTodo) {
                // 打开待办编辑器
                startActivity(new Intent(this, TodoEditor.class).setData(uri));
            } else {
                // 打开普通笔记编辑器
                startActivity(new Intent(Intent.ACTION_EDIT, uri));
            }
        }
    }
}