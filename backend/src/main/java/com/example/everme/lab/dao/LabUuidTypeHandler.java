package com.example.everme.lab.dao;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/** PostgreSQL UUID is not a built-in MyBatis simple type. Registered only in clone-lab. */
@MappedTypes(UUID.class)
public class LabUuidTypeHandler extends BaseTypeHandler<UUID> {
  @Override
  public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType type)
      throws SQLException {
    statement.setObject(index, value);
  }

  @Override
  public UUID getNullableResult(ResultSet result, String column) throws SQLException {
    return result.getObject(column, UUID.class);
  }

  @Override
  public UUID getNullableResult(ResultSet result, int column) throws SQLException {
    return result.getObject(column, UUID.class);
  }

  @Override
  public UUID getNullableResult(CallableStatement result, int column) throws SQLException {
    return result.getObject(column, UUID.class);
  }
}
