/*
 * Copyright (c) 2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.ttzero.excel.entity;

import cn.ttzero.excel.reader.Cell;
import cn.ttzero.excel.util.StringUtil;
import cn.ttzero.excel.annotation.DisplayName;
import cn.ttzero.excel.annotation.NotExport;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static cn.ttzero.excel.manager.Const.ROW_BLOCK_SIZE;

/**
 * Created by guanquan.wang at 2018-01-26 14:48
 */
public class ListObjectSheet<T> extends Sheet {
    private List<T> data;
    private Field[] fields;

    public ListObjectSheet(Workbook workbook) {
        super(workbook);
    }

    public ListObjectSheet(Workbook workbook, String name, Column[] columns) {
        super(workbook, name, columns);
    }

    public ListObjectSheet(Workbook workbook, String name, WaterMark waterMark, Column[] columns) {
        super(workbook, name, waterMark, columns);
    }


    @Override
    public void close() throws IOException {
        data.clear();
        data = null;
        super.close();
    }

    public ListObjectSheet<T> setData(final List<T> data) {
        this.data = data;
        return this;
    }

    @Override
    public RowBlock nextBlock() {
        // clear first
        rowBlock.clear();

        try {
            loopData();
        } catch (IllegalAccessException e) {
            throw new ExcelWriteException(e);
        }

        return rowBlock.flip();
    }

    private void loopData() throws IllegalAccessException {
        int end = getEndIndex();
        List<T> sub = data.subList(rows, end);
        int len = columns.length;
        for (T o : sub) {
            Row row = rowBlock.next();
            row.index = rows++;
            Field field;
            Cell[] cells = row.realloc(len);
            for (int i = 0; i < len; i++) {
                field = fields[i];
                // clear cells
                Cell cell = cells[i];
                cell.clear();

                Object e = field.get(o);
                // blank cell
                if (e == null) {
                    cell.setBlank();
                    continue;
                }

                setCellValue(cell, e, columns[i]);
            }
        }
    }

    private int getEndIndex() {
        int end = rows + ROW_BLOCK_SIZE;
        return end <= data.size() ? end : data.size();
    }

    private static final String[] exclude = {"serialVersionUID", "this$0"};

    private Field[] init() {
        Object o = workbook.getFirst(data);
        if (o == null) return null;
        if (columns == null || columns.length == 0) {
            Field[] fields = o.getClass().getDeclaredFields();
            List<Column> list = new ArrayList<>(fields.length);
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                String gs = field.toGenericString();
                NotExport notExport = field.getAnnotation(NotExport.class);
                if (notExport != null || StringUtil.indexOf(exclude, gs.substring(gs.lastIndexOf('.') + 1)) >= 0) {
                    fields[i] = null;
                    continue;
                }
                DisplayName dn = field.getAnnotation(DisplayName.class);
                if (dn != null && StringUtil.isNotEmpty(dn.value())) {
                    list.add(new Column(dn.value(), field.getName(), field.getType()).setShare(dn.share()));
                } else {
                    list.add(new Column(field.getName(), field.getName(), field.getType()).setShare(dn != null && dn.share()));
                }
            }
            columns = new Column[list.size()];
            list.toArray(columns);
            for (int i = 0; i < columns.length; i++) {
                columns[i].styles = workbook.getStyles();
            }
            // clear not export fields
            for (int len = fields.length, n = len - 1; n >= 0; n--) {
                if (fields[n] != null) {
                    fields[n].setAccessible(true);
                    continue;
                }
                if (n < len - 1) {
                    System.arraycopy(fields, n + 1, fields, n, len - n - 1);
                }
                len--;
            }
            return fields;
        } else {
            Field[] fields = new Field[columns.length];
            Class<?> clazz = o.getClass();
            for (int i = 0; i < columns.length; i++) {
                Column hc = columns[i];
                try {
                    fields[i] = clazz.getDeclaredField(hc.key);
                    fields[i].setAccessible(true);
                    if (hc.getClazz() == null) {
                        hc.setClazz(fields[i].getType());
//                        DisplayName dn = field.getAnnotation(DisplayName.class);
//                        if (dn != null) {
//                            hc.setShare(hc.isShare() || dn.share());
//                            if (StringUtil.isEmpty(hc.getName())
//                                    && StringUtil.isNotEmpty(dn.value())) {
//                                hc.setName(dn.value());
//                            }
//                        }
                    }
                } catch (NoSuchFieldException e) {
                    throw new ExcelWriteException("Column " + hc.getName() + " not declare in class " + clazz);
                }
            }
            return fields;
        }

    }

    /**
     * Returns the header column info
     * @return array of column
     */
    @Override
    public Column[] getHeaderColumns() {
        if (!headerReady) {
            if (data == null || data.isEmpty()) {
                columns = new Column[0];
            }
            // create header columns
            fields = init();
            if (fields == null || fields.length == 0 || fields[0] == null) {
                columns = new Column[0];
            }
            headerReady = true;
        }
        return columns;
    }

    /**
     * Returns total rows in this worksheet
     * @return -1 if unknown
     */
    @Override
    public int size() {
        return data != null ? data.size() : 0;
    }


}
