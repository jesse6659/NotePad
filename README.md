实验目的

本次中期实验旨在原有基础记事本应用的基础上，扩展和增强应用功能，实现一个功能更加完善的个人笔记管理工具。通过本次实验，将学习Android应用开发中的数据存储、UI设计、菜单系统、内容提供者等核心技术的综合应用。主要实现了NoteList界面中笔记条目增加时间戳显示、添加笔记查询功能（根据标题或内容查询）、添加代办功能、笔记分类。

实验环境
开发平台：Android Studio

目标SDK：API 23 (Android 6.0)

开发语言：Java

数据库：SQLite


1. 主界面与时间戳显示
<img width="563" height="1041" alt="image" src="https://github.com/user-attachments/assets/5ffd5929-c591-4520-9bc0-dce0c1433738" />



text
示例截图说明：
- 笔记列表显示标题、时间戳和分类信息
- 已完成待办事项有删除线效果
- 搜索框位于顶部
2. 搜索功能演示
<img width="564" height="504" alt="image" src="https://github.com/user-attachments/assets/f251c207-4748-4b5f-934b-4c372d6b10dd" />


text
示例代码 - 搜索实现：
@Override
public boolean onQueryTextChange(String newText) {
    // 构建搜索条件
    List<String> selectionParts = new ArrayList<>();
    List<String> argsList = new ArrayList<>();
    
    if (!TextUtils.isEmpty(newText)) {
        selectionParts.add("(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
                NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)");
        String searchPattern = "%" + newText + "%";
        argsList.add(searchPattern);
        argsList.add(searchPattern);
    }
    
    // 执行搜索查询
    String selection = selectionParts.isEmpty() ? null : 
                      TextUtils.join(" AND ", selectionParts);
    String[] selectionArgs = argsList.toArray(new String[0]);
    
    Cursor cursor = managedQuery(
            getIntent().getData(),
            PROJECTION,
            selection,
            selectionArgs,
            NotePad.Notes.DEFAULT_SORT_ORDER
    );
    
    // 更新列表显示
    setupAdapter(cursor);
    return true;
}
3. 待办事项编辑界面
<img width="577" height="1065" alt="image" src="https://github.com/user-attachments/assets/210fe542-9f1b-47b6-8bbd-92b79a3b2692" />


text
示例代码 - 待办事项保存：
private void saveTodo() {
    ContentValues values = new ContentValues();
    values.put(NotePad.Notes.COLUMN_NAME_TITLE, mTitleText.getText().toString());
    values.put(NotePad.Notes.COLUMN_NAME_NOTE, mDescriptionText.getText().toString());
    values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 1);
    values.put(NotePad.Notes.COLUMN_NAME_IS_COMPLETED, mCompletedCheckbox.isChecked() ? 1 : 0);
    values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
    
    // 设置优先级
    int priority = NotePad.Notes.PRIORITY_MEDIUM;
    int checkedId = mPriorityGroup.getCheckedRadioButtonId();
    if (checkedId == R.id.priority_low) {
        priority = NotePad.Notes.PRIORITY_LOW;
    } else if (checkedId == R.id.priority_high) {
        priority = NotePad.Notes.PRIORITY_HIGH;
    }
    values.put(NotePad.Notes.COLUMN_NAME_PRIORITY, priority);
    
    // 设置截止日期
    if (mDueDate != null) {
        values.put(NotePad.Notes.COLUMN_NAME_DUE_DATE, mDueDate.getTimeInMillis());
    }
    
    getContentResolver().update(mUri, values, null, null);
    Toast.makeText(this, "待办事项已保存", Toast.LENGTH_SHORT).show();
}
4. 分类管理界面
<img width="583" height="848" alt="image" src="https://github.com/user-attachments/assets/42ae619e-a9f0-4a48-b0e3-cc00f240a7ea" />


text
示例代码 - 分类管理：
public class CategoryManager extends Activity {
    private List<Category> categories = new ArrayList<>();
    
    private void loadCategories() {
        categories.clear();
        Cursor cursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{
                        NotePad.Categories._ID,
                        NotePad.Categories.COLUMN_NAME_CATEGORY_NAME,
                        NotePad.Categories.COLUMN_NAME_CATEGORY_COLOR
                },
                null, null, NotePad.Categories.COLUMN_NAME_CREATE_DATE + " ASC"
        );
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Category category = new Category();
                category.id = cursor.getLong(0);
                category.name = cursor.getString(1);
                category.color = cursor.getInt(2);
                categories.add(category);
            }
            cursor.close();
        }
        adapter.notifyDataSetChanged();
    }
}
5. 分类筛选功能
<img width="565" height="1025" alt="image" src="https://github.com/user-attachments/assets/6f55fa02-1525-4245-b10c-8d702cb15de2" />


text
示例代码 - 分类筛选菜单：
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
            new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_CATEGORY_NAME},
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
6. 待办事项筛选
<img width="568" height="993" alt="image" src="https://github.com/user-attachments/assets/09bd0537-52a5-4f0b-bfdf-e531dc711ff1" />


text
示例代码 - 待办筛选实现：
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
实验内容与实现
一、初始应用功能分析
原始记事本应用具备以下基本功能：

新建和编辑笔记：用户可以创建新笔记并进行文本编辑

编辑标题：通过菜单选项单独编辑笔记标题

笔记列表：主界面显示所有笔记的标题列表

二、功能扩展实现
1. 笔记列表时间戳显示
功能要求：为每个笔记添加创建时间和修改时间显示

实现方案：

修改数据库表结构，添加创建时间和修改时间字段

更新笔记列表项布局，添加时间显示TextView

在新建和修改笔记时自动记录时间

时间格式化为"yyyy-MM-dd HH:mm"格式

关键技术：

java
// 时间格式化工具方法
private String formatDate(long timestamp) {
    Date date = new Date(timestamp);
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    return sdf.format(date);
}

// 在ViewBinder中设置时间显示
mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
    @Override
    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        if (view.getId() == R.id.timestamp) {
            TextView textView = (TextView) view;
            long timestamp = cursor.getLong(columnIndex);
            String formattedDate = formatDate(timestamp);
            textView.setText(formattedDate);
            return true;
        }
        return false;
    }
});
2. 笔记搜索功能
功能要求：实现基于标题和内容的全文搜索

实现方案：

在主界面添加搜索按钮和搜索输入框

实现动态搜索，输入时实时显示匹配结果

支持多条件搜索（标题+内容）

关键技术：

java
// 搜索布局初始化
private void initSearchView() {
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
    mSearchLayout.setVisibility(View.GONE);
}
3. 待办事项功能
功能要求：将笔记扩展为支持待办事项管理

实现方案：

扩展数据库结构，添加待办相关字段（是否待办、完成状态、优先级、截止日期）

创建专门的待办事项编辑界面

实现待办事项的筛选和分类

添加待办完成状态快速切换功能

数据库扩展：

java
// 数据库版本升级到4
private static final int DATABASE_VERSION = 4;

// 数据库表结构更新
@Override
public void onCreate(SQLiteDatabase db) {
    // 创建分类表
    db.execSQL("CREATE TABLE " + NotePad.Categories.TABLE_NAME + " (" +
            NotePad.Categories._ID + " INTEGER PRIMARY KEY," +
            NotePad.Categories.COLUMN_NAME_CATEGORY_NAME + " TEXT UNIQUE," +
            NotePad.Categories.COLUMN_NAME_CATEGORY_COLOR + " INTEGER," +
            NotePad.Categories.COLUMN_NAME_CREATE_DATE + " INTEGER" +
            ");");
    
    // 创建笔记表（添加待办功能字段）
    db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " (" +
            NotePad.Notes._ID + " INTEGER PRIMARY KEY," +
            NotePad.Notes.COLUMN_NAME_TITLE + " TEXT," +
            NotePad.Notes.COLUMN_NAME_NOTE + " TEXT," +
            NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER," +
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER," +
            NotePad.Notes.COLUMN_NAME_CATEGORY_ID + " INTEGER DEFAULT 1," +
            NotePad.Notes.COLUMN_NAME_IS_TODO + " INTEGER DEFAULT 0," +
            NotePad.Notes.COLUMN_NAME_IS_COMPLETED + " INTEGER DEFAULT 0," +
            NotePad.Notes.COLUMN_NAME_DUE_DATE + " INTEGER," +
            NotePad.Notes.COLUMN_NAME_PRIORITY + " INTEGER DEFAULT 0," +
            "FOREIGN KEY(" + NotePad.Notes.COLUMN_NAME_CATEGORY_ID + ") REFERENCES " +
            NotePad.Categories.TABLE_NAME + "(" + NotePad.Categories._ID + ")" +
            ");");
}
4. 笔记分类功能
功能要求：实现笔记和待办事项的分类管理

实现方案：

新增分类表，支持分类的增删改查

笔记与分类建立外键关联

实现分类筛选和分类切换功能

创建分类管理界面

关键技术实现：

java
// 分类选择对话框完整实现
public class CategorySelectDialog extends Dialog {
    public interface OnCategorySelectedListener {
        void onCategorySelected(long categoryId, String categoryName);
    }
    
    private void loadCategories() {
        categories.clear();
        Cursor cursor = context.getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_CATEGORY_NAME},
                null, null, NotePad.Categories.COLUMN_NAME_CREATE_DATE + " ASC"
        );
        
        if (cursor != null) {
            List<String> categoryNames = new ArrayList<>();
            while (cursor.moveToNext()) {
                CategoryItem category = new CategoryItem();
                category.id = cursor.getLong(0);
                category.name = cursor.getString(1);
                categories.add(category);
                categoryNames.add(category.name);
            }
            cursor.close();
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    context, android.R.layout.simple_spinner_item, categoryNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorySpinner.setAdapter(adapter);
        }
    }
}
系统架构设计
1. 数据库设计
text
分类表(categories)
├── _id (主键)
├── name (分类名称)
├── color (分类颜色)
└── created (创建时间)

笔记表(notes)
├── _id (主键)
├── title (标题)
├── note (内容)
├── created (创建时间)
├── modified (修改时间)
├── category_id (外键，关联分类)
├── is_todo (是否待办)
├── is_completed (是否完成)
├── due_date (截止日期)
└── priority (优先级)
2. 核心类设计
NotesList：主列表Activity，集成笔记和待办显示、搜索、分类筛选

NoteEditor：普通笔记编辑Activity

TodoEditor：待办事项编辑Activity

CategoryManager：分类管理Activity

NotePadProvider：内容提供者，管理数据存储

CategorySelectDialog：分类选择对话框

3. 用户界面设计
主界面：集成搜索框、笔记列表、多种筛选选项

编辑界面：区分普通笔记和待办事项编辑

分类管理界面：直观的分类列表和操作

功能特色
一体化设计：将笔记和待办事项统一管理

智能搜索：支持标题和内容的全文搜索

灵活分类：支持多级分类和分类筛选

优先级管理：待办事项支持优先级设置

截止日期：为待办事项设置完成期限

实时筛选：多种筛选条件的实时应用

实现效果
主界面
显示所有笔记和待办事项

顶部搜索框支持实时搜索

菜单提供新建笔记、新建待办、分类管理等功能

支持按分类、待办状态进行筛选

笔记编辑
普通笔记的文本编辑功能

支持分类选择和切换

待办编辑
待办事项的详细属性设置

优先级选择（低、中、高）

截止日期选择

完成状态标记

分类管理
分类的增删改查

分类颜色标识

笔记分类统计

技术难点与解决方案
1. 数据库版本升级
问题：添加新功能需要修改数据库结构
解决方案：实现DatabaseHelper的onUpgrade方法，兼容旧版本数据

java
@Override
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.w(TAG, "Upgrading database from version " + oldVersion + " to " +
            newVersion + ", which will destroy all old data");
    
    if (oldVersion < 4) {
        // 升级到版本4：添加待办功能
        db.execSQL("DROP TABLE IF EXISTS categories");
        db.execSQL("DROP TABLE IF EXISTS notes");
        onCreate(db);
    }
}
2. 实时搜索性能
问题：大量数据时实时搜索可能卡顿
解决方案：使用数据库的LIKE查询，优化查询语句

java
// 优化的搜索查询
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
    
    if (!selectionParts.isEmpty()) {
        selection = TextUtils.join(" AND ", selectionParts);
        selectionArgs = argsList.toArray(new String[0]);
    }
    
    // 执行查询
    Cursor cursor = managedQuery(
            getIntent().getData(),
            PROJECTION,
            selection,
            selectionArgs,
            NotePad.Notes.DEFAULT_SORT_ORDER
    );
    
    setupAdapter(cursor);
}
3. 界面复用
问题：笔记和待办需要不同的编辑界面
解决方案：创建独立的TodoEditor，根据数据类型跳转到相应界面

java
// 智能跳转到对应编辑器
@Override
protected void onListItemClick(ListView l, View v, int position, long id) {
    // ... 位置调整代码
    
    // 检查是否为待办事项
    boolean isTodo = cursor.getInt(COLUMN_INDEX_IS_TODO) == 1;
    
    // 根据是否为待办事项打开不同的编辑器
    if (isTodo) {
        // 打开待办编辑器
        startActivity(new Intent(this, TodoEditor.class).setData(uri));
    } else {
        // 打开普通笔记编辑器
        startActivity(new Intent(Intent.ACTION_EDIT, uri));
    }
}
实验总结
通过本次中期实验，成功将基础记事本应用扩展为功能完善的个人知识管理工具。主要收获包括：

掌握了Android数据库设计和升级策略

理解了内容提供者(Content Provider)的工作原理

学会了复杂UI界面的设计和实现

实践了Android菜单系统和对话框的使用

掌握了数据绑定和列表显示的高级技巧

本次实验不仅增强了技术实践能力，也为后续更复杂的Android应用开发打下了坚实基础。未来可以在此基础上继续扩展功能，如云同步、标签系统、附件支持等，打造更加完善的个人知识管理系统。
