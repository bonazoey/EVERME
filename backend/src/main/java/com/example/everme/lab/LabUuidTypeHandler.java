package com.example.everme.lab;

import org.apache.ibatis.type.*;
import java.sql.*;
import java.util.UUID;

/** PostgreSQL UUID is not a built-in MyBatis simple type. Registered only in clone-lab. */
@MappedTypes(UUID.class)
public class LabUuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement statement,int index,UUID value,JdbcType type) throws SQLException {statement.setObject(index,value);}
    @Override public UUID getNullableResult(ResultSet result,String column) throws SQLException {return result.getObject(column,UUID.class);}
    @Override public UUID getNullableResult(ResultSet result,int column) throws SQLException {return result.getObject(column,UUID.class);}
    @Override public UUID getNullableResult(CallableStatement result,int column) throws SQLException {return result.getObject(column,UUID.class);}
}
