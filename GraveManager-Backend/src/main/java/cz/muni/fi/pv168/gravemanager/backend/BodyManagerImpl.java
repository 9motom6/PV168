package cz.muni.fi.pv168.gravemanager.backend;

import cz.muni.fi.pv168.common.DBUtils;
import cz.muni.fi.pv168.common.IllegalEntityException;
import cz.muni.fi.pv168.common.ServiceFailureException;
import cz.muni.fi.pv168.common.ValidationException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * This class implements BodyManager service.
 * 
 * @author Petr Adámek
 */
public class BodyManagerImpl implements BodyManager {
    
    private static final Logger logger = Logger.getLogger(
            BodyManagerImpl.class.getName());    
    
    private DataSource dataSource;
    private final Clock clock;

    public BodyManagerImpl(Clock clock) {
        this.clock = clock;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private void checkDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not set");
        }
    }

    @Override
    public List<Body> findAllBodies() throws ServiceFailureException {
        checkDataSource();
        Connection conn = null;
        PreparedStatement st = null;
        try {
            conn = dataSource.getConnection();
            st = conn.prepareStatement(
                    "SELECT id, name, gender, born, died, vampire FROM Body");
            return executeQueryForMultipleBodies(st);
        } catch (SQLException ex) {
            String msg = "Error when getting all bodies from DB";
            logger.log(Level.SEVERE, msg, ex);
            throw new ServiceFailureException(msg, ex);
        } finally {
            DBUtils.closeQuietly(conn, st);
        }          
    }

    @Override
    public void createBody(Body body) throws ServiceFailureException {
        checkDataSource();
        validate(body);
        if (body.getId() != null) {
            throw new IllegalEntityException("body id is already set");
        }       
        Connection conn = null;
        PreparedStatement st = null;
        try {
            conn = dataSource.getConnection();
            // Temporary turn autocommit mode off. It is turned back on in 
            // method DBUtils.closeQuietly(...) 
            conn.setAutoCommit(false);
            st = conn.prepareStatement(
                    "INSERT INTO Body (name,gender,born,died,vampire) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            st.setString(1, body.getName());
            st.setString(2, toString(body.getGender()));

            // This is the proper way, how to handle LocalDate, however it is not
            // supported by Derby yet - see https://issues.apache.org/jira/browse/DERBY-6445
            //st.setObject(3, body.getBorn());
            //st.setObject(4, body.getDied());

            st.setDate(3, toSqlDate(body.getBorn()));
            st.setDate(4, toSqlDate(body.getDied()));
            st.setInt(5, body.isVampire()?1:0);

            int count = st.executeUpdate();
            DBUtils.checkUpdatesCount(count, body, true);

            Long id = DBUtils.getId(st.getGeneratedKeys());
            body.setId(id);
            conn.commit();
        } catch (SQLException ex) {
            String msg = "Error when inserting grave into db";
            logger.log(Level.SEVERE, msg, ex);
            throw new ServiceFailureException(msg, ex);
        } finally {
            DBUtils.doRollbackQuietly(conn);
            DBUtils.closeQuietly(conn, st);
        }
    }

    @Override
    public Body getBody(Long id) throws ServiceFailureException {

        checkDataSource();
        
        if (id == null) {
            throw new IllegalArgumentException("id is null");
        }
        
        Connection conn = null;
        PreparedStatement st = null;
        try {
            conn = dataSource.getConnection();
            st = conn.prepareStatement(
                    "SELECT id, name, gender, born, died, vampire FROM Body WHERE id = ?");
            st.setLong(1, id);
            return executeQueryForSingleBody(st);
        } catch (SQLException ex) {
            String msg = "Error when getting body with id = " + id + " from DB";
            logger.log(Level.SEVERE, msg, ex);
            throw new ServiceFailureException(msg, ex);
        } finally {
            DBUtils.closeQuietly(conn, st);
        }
    }

    @Override
    public void updateBody(Body body) throws ServiceFailureException {
        checkDataSource();
        validate(body);
        
        if (body.getId() == null) {
            throw new IllegalEntityException("body id is null");
        }        
        Connection conn = null;
        PreparedStatement st = null;
        try {
            conn = dataSource.getConnection();
            // Temporary turn autocommit mode off. It is turned back on in 
            // method DBUtils.closeQuietly(...) 
            conn.setAutoCommit(false);            
            st = conn.prepareStatement(
                    "UPDATE Body SET name = ?, gender = ?, born = ?, died = ?, vampire = ? WHERE id = ?");
            st.setString(1, body.getName());
            st.setString(2, toString(body.getGender()));

            // This is the proper way, how to handle LocalDate, however it is not
            // supported by Derby yet - see https://issues.apache.org/jira/browse/DERBY-6445
            // st.setObject(3, body.getBorn());
            // st.setObject(4, body.getDied());

            st.setDate(3, toSqlDate(body.getBorn()));
            st.setDate(4, toSqlDate(body.getDied()));
            st.setInt(5, body.isVampire()?1:0);
            st.setLong(6, body.getId());

            int count = st.executeUpdate();
            DBUtils.checkUpdatesCount(count, body, false);
            conn.commit();
        } catch (SQLException ex) {
            String msg = "Error when updating body in the db";
            logger.log(Level.SEVERE, msg, ex);
            throw new ServiceFailureException(msg, ex);
        } finally {
            DBUtils.doRollbackQuietly(conn);
            DBUtils.closeQuietly(conn, st);
        }        
    }


    @Override
    public void deleteBody(Body body) throws ServiceFailureException {
        checkDataSource();
        if (body == null) {
            throw new IllegalArgumentException("body is null");
        }        
        if (body.getId() == null) {
            throw new IllegalEntityException("body id is null");
        }        
        Connection conn = null;
        PreparedStatement st = null;
        try {
            conn = dataSource.getConnection();
            // Temporary turn autocommit mode off. It is turned back on in 
            // method DBUtils.closeQuietly(...) 
            conn.setAutoCommit(false);
            st = conn.prepareStatement(
                    "DELETE FROM Body WHERE id = ?");
            st.setLong(1, body.getId());

            int count = st.executeUpdate();
            DBUtils.checkUpdatesCount(count, body, false);
            conn.commit();
        } catch (SQLException ex) {
            String msg = "Error when deleting body from the db";
            logger.log(Level.SEVERE, msg, ex);
            throw new ServiceFailureException(msg, ex);
        } finally {
            DBUtils.doRollbackQuietly(conn);
            DBUtils.closeQuietly(conn, st);
        }
    }

    static Body executeQueryForSingleBody(PreparedStatement st) throws SQLException, ServiceFailureException {
        ResultSet rs = st.executeQuery();
        if (rs.next()) {
            Body result = rowToBody(rs);                
            if (rs.next()) {
                throw new ServiceFailureException(
                        "Internal integrity error: more bodies with the same id found!");
            }
            return result;
        } else {
            return null;
        }
    }

    static List<Body> executeQueryForMultipleBodies(PreparedStatement st) throws SQLException {
        ResultSet rs = st.executeQuery();
        List<Body> result = new ArrayList<Body>();
        while (rs.next()) {
            result.add(rowToBody(rs));
        }
        return result;
    }

    static private Body rowToBody(ResultSet rs) throws SQLException {
        Body result = new Body();
        result.setId(rs.getLong("id"));
        result.setName(rs.getString("name"));
        result.setGender(toGender(rs.getString("gender")));

        // This is the proper way, how to handle LocalDate, however it is not
        // supported by Derby yet - see https://issues.apache.org/jira/browse/DERBY-6445
        //result.setBorn(rs.getObject("born", LocalDate.class));
        //result.setDied(rs.getObject("died", LocalDate.class));

        result.setBorn(toLocalDate(rs.getDate("born")));
        result.setDied(toLocalDate(rs.getDate("died")));
        result.setVampire(rs.getInt("vampire") != 0);
        return result;
    }

    private void validate(Body body) {
        if (body == null) {
            throw new IllegalArgumentException("grave is null");
        }
        if (body.getName() == null) {
            throw new ValidationException("name is null");
        }
        if (body.getGender() == null) {
            throw new ValidationException("gender is null");
        }
        if (body.getBorn() != null && body.getDied() != null && body.getDied().isBefore(body.getBorn())) {
            throw new ValidationException("died is before born");
        }
        LocalDate today = LocalDate.now(clock);
        if (body.getBorn() != null && body.getBorn().isAfter(today)) {
            throw new ValidationException("born is in future");
        }
        if (body.getDied() != null && body.getDied().isAfter(today)) {
            throw new ValidationException("died is in future");
        }
    }
    
    private static Gender toGender(String gender) {
        return gender == null ? null : Gender.valueOf(gender);
    }

    private static String toString(Gender gender) {
        return gender == null ? null : gender.name();
    }

    private static Date toSqlDate(LocalDate localDate) {
        return localDate == null ? null : Date.valueOf(localDate);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

}
