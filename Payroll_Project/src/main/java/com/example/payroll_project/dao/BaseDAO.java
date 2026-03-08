package com.example.payroll_project.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Base DAO interface defining common CRUD operations
 * All entity DAOs should implement this interface
 * 
 */
public interface BaseDAO<T, ID> {

    T create(T entity) throws SQLException;
    Optional<T> findById(ID id) throws SQLException;
    List<T> findAll() throws SQLException;
    boolean update(T entity) throws SQLException;
    boolean delete(ID id) throws SQLException;
    boolean exists(ID id) throws SQLException;
    long count() throws SQLException;
}
