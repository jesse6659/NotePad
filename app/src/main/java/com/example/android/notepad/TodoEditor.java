package com.example.android.notepad;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TodoEditor extends Activity {

    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_IS_TODO,
            NotePad.Notes.COLUMN_NAME_IS_COMPLETED,
            NotePad.Notes.COLUMN_NAME_DUE_DATE,
            NotePad.Notes.COLUMN_NAME_PRIORITY
    };

    private Uri mUri;
    private Cursor mCursor;

    private EditText mTitleText;
    private EditText mDescriptionText;
    private CheckBox mCompletedCheckbox;
    private RadioGroup mPriorityGroup;
    private Button mDueDateButton;
    private TextView mDueDateText;

    private Calendar mDueDate;
    private SimpleDateFormat mDateFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.todo_editor);

        mUri = getIntent().getData();
        mDateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        initializeViews();
        loadTodoData();
    }

    private void initializeViews() {
        mTitleText = (EditText) findViewById(R.id.todo_title);
        mDescriptionText = (EditText) findViewById(R.id.todo_description);
        mCompletedCheckbox = (CheckBox) findViewById(R.id.todo_completed);
        mPriorityGroup = (RadioGroup) findViewById(R.id.priority_group);
        mDueDateButton = (Button) findViewById(R.id.due_date_button);
        mDueDateText = (TextView) findViewById(R.id.due_date_text);

        mDueDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });
    }

    private void loadTodoData() {
        mCursor = managedQuery(mUri, PROJECTION, null, null, null);

        if (mCursor != null && mCursor.moveToFirst()) {
            mTitleText.setText(mCursor.getString(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE)));
            mDescriptionText.setText(mCursor.getString(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE)));

            // 加载完成状态
            boolean isCompleted = mCursor.getInt(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_IS_COMPLETED)) == 1;
            mCompletedCheckbox.setChecked(isCompleted);

            // 加载优先级
            int priority = mCursor.getInt(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_PRIORITY));
            switch (priority) {
                case NotePad.Notes.PRIORITY_LOW:
                    mPriorityGroup.check(R.id.priority_low);
                    break;
                case NotePad.Notes.PRIORITY_MEDIUM:
                    mPriorityGroup.check(R.id.priority_medium);
                    break;
                case NotePad.Notes.PRIORITY_HIGH:
                    mPriorityGroup.check(R.id.priority_high);
                    break;
            }

            // 加载截止日期
            long dueDate = mCursor.getLong(mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_DUE_DATE));
            if (dueDate > 0) {
                mDueDate = Calendar.getInstance();
                mDueDate.setTimeInMillis(dueDate);
                mDueDateText.setText(mDateFormatter.format(mDueDate.getTime()));
            }
        }
    }

    private void showDatePicker() {
        Calendar currentDate = mDueDate != null ? mDueDate : Calendar.getInstance();

        DatePickerDialog datePicker = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(android.widget.DatePicker view, int year, int month, int dayOfMonth) {
                        if (mDueDate == null) {
                            mDueDate = Calendar.getInstance();
                        }
                        mDueDate.set(year, month, dayOfMonth);
                        mDueDateText.setText(mDateFormatter.format(mDueDate.getTime()));
                    }
                },
                currentDate.get(Calendar.YEAR),
                currentDate.get(Calendar.MONTH),
                currentDate.get(Calendar.DAY_OF_MONTH));

        datePicker.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.todo_editor_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 使用 if-else 语句代替 switch，因为资源ID在编译时不是常量
        int id = item.getItemId();
        if (id == R.id.menu_save_todo) {
            saveTodo();
            finish();
            return true;
        } else if (id == R.id.menu_delete_todo) {
            deleteTodo();
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void saveTodo() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, mTitleText.getText().toString());
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, mDescriptionText.getText().toString());
        values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 1); // 标记为待办事项
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
        } else {
            values.putNull(NotePad.Notes.COLUMN_NAME_DUE_DATE);
        }

        getContentResolver().update(mUri, values, null, null);
        Toast.makeText(this, "待办事项已保存", Toast.LENGTH_SHORT).show();
    }

    private void deleteTodo() {
        getContentResolver().delete(mUri, null, null);
        Toast.makeText(this, "待办事项已删除", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCursor != null) {
            saveTodo();
        }
    }
}