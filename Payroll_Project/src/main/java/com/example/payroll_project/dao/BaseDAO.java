package com.example.payroll_project.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Base DAO interface defining common CRUD operations
 * All entity DAOs should implement this interface
 * 
 * @param <T> Entity type
 * @param <ID> Primary key type
 */
public interface BaseDAO<T, ID> {
    
    /**
     * Create a new entity
     * @param entity Entity to create
     * @return Created entity with generated ID
     */
    T create(T entity) throws SQLException;
    
    /**
     * Find entity by ID
     * @param id Primary key
     * @return Optional containing entity if found
     */
    Optional<T> findById(ID id) throws SQLException;
    
    /**
     * Find all entities
     * @return List of all entities
     */
    List<T> findAll() throws SQLException;
    
    /**
     * Update existing entity
     * @param entity Entity with updated values
     * @return true if update successful
     */
    boolean update(T entity) throws SQLException;
    
    /**
     * Delete entity by ID
     * @param id Primary key
     * @return true if deletion successful
     */
    boolean delete(ID id) throws SQLException;
    
    /**
     * Check if entity exists by ID
     * @param id Primary key
     * @return true if entity exists
     */
    boolean exists(ID id) throws SQLException;
    
    /**
     * Count total entities
     * @return Total count
     */
    long count() throws SQLException;
}
