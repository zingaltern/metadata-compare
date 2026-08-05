package com.dqc.compare.parser.impl;

import com.dqc.compare.model.FieldMeta;
import com.dqc.compare.model.SourceType;
import com.dqc.compare.model.StandardMetadata;
import com.dqc.compare.parser.MetadataParser;
import com.dqc.compare.parser.ParseRequest;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产 DDL 解析器（.sql / CREATE TABLE），基于 JSqlParser。
 */
@Component
public class MySqlDdlParser implements MetadataParser {

    private static final Logger log = LoggerFactory.getLogger(MySqlDdlParser.class);

    @Override
    public boolean supports(ParseRequest req) {
        return req.getFileName().toLowerCase().endsWith(".sql");
    }

    @Override
    public List<StandardMetadata> parse(ParseRequest req) throws Exception {
        List<StandardMetadata> result = new ArrayList<>();
        String sql = Files.readString(req.getPath());
        try {
            var statements = CCJSqlParserUtil.parseStatements(sql);
            for (Statement stmt : statements.getStatements()) {
                if (stmt instanceof CreateTable create) {
                    StandardMetadata meta = parseCreate(create);
                    if (meta != null) {
                        result.add(meta);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 DDL 文件失败，已跳过: {} -> {}", req.getFileName(), e.getMessage());
        }
        return result;
    }

    private StandardMetadata parseCreate(CreateTable create) {
        String tableName = tableNameOf(create);
        if (tableName == null) {
            return null;
        }
        StandardMetadata meta = new StandardMetadata(SourceType.PRODUCTION_DDL, tableName);
        List<ColumnDefinition> cols = create.getColumnDefinitions();
        if (cols == null) {
            return meta;
        }
        for (ColumnDefinition col : cols) {
            FieldMeta fm = new FieldMeta();
            String colName = col.getColumnName();
            fm.setFieldName(colName == null ? null : colName.replace("`", ""));
            ColDataType cdt = col.getColDataType();
            if (cdt != null) {
                fm.setDataType(cdt.getDataType());
                List<String> args = cdt.getArgumentsStringList();
                if (args != null && !args.isEmpty()) {
                    fm.setLength(parseInt(args.get(0)));
                    if (args.size() >= 2) {
                        fm.setPrecision(parseInt(args.get(1)));
                    }
                }
            }
            List<String> specs = col.getColumnSpecs();
            if (specs != null) {
                boolean hasNot = specs.contains("NOT");
                boolean hasNull = specs.contains("NULL");
                fm.setNullable(!(hasNot && hasNull));
                if (specs.contains("PRIMARY") && specs.contains("KEY")) {
                    fm.setConstraint("PK");
                }
                // COMMENT 紧跟在 "COMMENT" 之后
                for (int i = 0; i < specs.size(); i++) {
                    if ("COMMENT".equalsIgnoreCase(specs.get(i)) && i + 1 < specs.size()) {
                        fm.setComment(stripQuotes(specs.get(i + 1)));
                        break;
                    }
                }
            }
            meta.getFields().add(fm);
        }
        // 表级约束：复合/单列 PRIMARY KEY 补齐字段的 PK 标记
        if (create.getIndexes() != null) {
            for (Index idx : create.getIndexes()) {
                if (idx.getType() != null && idx.getType().toUpperCase().contains("PRIMARY")) {
                    List<String> pkCols = idx.getColumnsNames();
                    if (pkCols != null) {
                        for (String pk : pkCols) {
                            markPrimaryKey(meta, pk);
                        }
                    }
                }
            }
        }
        log.debug("DDL 解析完成：{} -> {} 个字段", tableName, meta.getFields().size());
        return meta;
    }

    private void markPrimaryKey(StandardMetadata meta, String rawColName) {
        if (rawColName == null) {
            return;
        }
        String colName = rawColName.replace("`", "");
        for (FieldMeta f : meta.getFields()) {
            if (colName.equalsIgnoreCase(f.getFieldName())) {
                String cur = f.getConstraint();
                if (cur == null || cur.isBlank()) {
                    f.setConstraint("PK");
                } else if (!cur.toUpperCase().contains("PK")) {
                    f.setConstraint(cur + ",PK");
                }
                break;
            }
        }
    }

    private String tableNameOf(CreateTable create) {
        if (create.getTable() == null) {
            return null;
        }
        String name = create.getTable().getName();
        if (name == null) {
            name = create.getTable().getFullyQualifiedName();
        }
        if (name == null) {
            return null;
        }
        // 去掉反引号与 schema 前缀
        name = name.replace("`", "");
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return name;
    }

    private Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        String digits = s.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.parseInt(digits);
    }

    private String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.length() >= 2 && ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
