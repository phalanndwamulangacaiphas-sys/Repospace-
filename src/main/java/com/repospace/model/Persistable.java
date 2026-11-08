package com.repospace.model;

import java.sql.SQLException;

public interface Persistable {

    int getId();

    void save() throws SQLException;
}
