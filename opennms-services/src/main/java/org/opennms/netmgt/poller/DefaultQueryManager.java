//
//This file is part of the OpenNMS(R) Application.
//
//OpenNMS(R) is Copyright (C) 2002-2003 The OpenNMS Group, Inc.  All rights reserved.
//OpenNMS(R) is a derivative work, containing both original code, included code and modified
//code that was published under the GNU General Public License. Copyrights for modified 
//and included code are below.
//
//OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
//Modifications:
//
//2004 Nov 14:Created
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
//For more information contact:
//   OpenNMS Licensing       <license@opennms.org>
//   http://www.opennms.org/
//   http://www.opennms.com/
//
package org.opennms.netmgt.poller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.EventConstants;
import org.opennms.netmgt.config.OpennmsServerConfigFactory;
import org.opennms.netmgt.utils.Querier;
import org.opennms.netmgt.utils.SingleResultQuerier;
import org.opennms.netmgt.utils.Updater;

/**
 * @author brozow
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class DefaultQueryManager implements QueryManager {

    final static String SQL_RETRIEVE_INTERFACES = "SELECT nodeid,ipaddr FROM ifServices, service WHERE ifServices.serviceid = service.serviceid AND service.servicename = ? AND ifServices.status='A'";

    final static String SQL_RETRIEVE_SERVICE_IDS = "SELECT serviceid,servicename  FROM service";

    final static String SQL_RETRIEVE_SERVICE_STATUS = "SELECT ifregainedservice,iflostservice FROM outages WHERE nodeid = ? AND ipaddr = ? AND serviceid = ? AND iflostservice = (SELECT max(iflostservice) FROM outages WHERE nodeid = ? AND ipaddr = ? AND serviceid = ?)";

    /**
     * SQL statement used to query the 'ifServices' for a nodeid/ipaddr/service
     * combination on the receipt of a 'nodeGainedService' to make sure there is
     * atleast one row where the service status for the tuple is 'A'.
     */
    final static String SQL_COUNT_IFSERVICE_STATUS = "select count(*) FROM ifServices, service WHERE nodeid=? AND ipaddr=? AND status='A' AND ifServices.serviceid=service.serviceid AND service.servicename=?";

    /**
     * SQL statement used to count the active ifservices on the specified ip
     * address.
     */
    final static String SQL_COUNT_IFSERVICES_TO_POLL = "SELECT COUNT(*) FROM ifservices WHERE status = 'A' AND ipaddr = ?";

    /**
     * SQL statement used to retrieve an active ifservice for the scheduler to
     * poll.
     */
    final static String SQL_FETCH_IFSERVICES_TO_POLL = "SELECT if.serviceid FROM ifservices if, service s WHERE if.serviceid = s.serviceid AND if.status = 'A' AND if.ipaddr = ?";

    private DataSource m_dataSource;

    public void setDataSource(DataSource dataSource) {
        m_dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return m_dataSource;
    }

    private Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }


    /**
     * @param whichEvent
     * @param nodeId
     * @param ipAddr
     * @param serviceName
     * @return
     */
    public boolean activeServiceExists(String whichEvent, int nodeId, String ipAddr, String serviceName) {
        Category log = log();
        java.sql.Connection dbConn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            dbConn = getConnection();

            stmt = dbConn.prepareStatement(DefaultQueryManager.SQL_COUNT_IFSERVICE_STATUS);

            stmt.setInt(1, nodeId);
            stmt.setString(2, ipAddr);
            stmt.setString(3, serviceName);

            rs = stmt.executeQuery();
            while (rs.next()) {
                return rs.getInt(1) > 0;
            }

            if (log.isDebugEnabled())
                log.debug(whichEvent + nodeId + "/" + ipAddr + "/" + serviceName + " active");
        } catch (SQLException sqlE) {
            log.error("SQLException during check to see if nodeid/ip/service is active", sqlE);
        } finally {
            try {
                // close the result set
                if (rs != null)
                    rs.close();
                // close the statement
                if (stmt != null)
                    stmt.close();
                // close the connection
                if (dbConn != null)
                    dbConn.close();
            } catch (SQLException sqlE) {                
            }

        }
        return false;
    }

    /**
     * @param ipaddr
     * @return
     * @throws SQLException
     */
    public List<Integer> getActiveServiceIdsForInterface(String ipaddr) throws SQLException {
        java.sql.Connection dbConn = getConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            List<Integer> serviceIds = new ArrayList<Integer>();
            Category log = log();
            stmt = dbConn.prepareStatement(DefaultQueryManager.SQL_FETCH_IFSERVICES_TO_POLL);
            stmt.setString(1, ipaddr);
            rs = stmt.executeQuery();
            if (log.isDebugEnabled())
                log.debug("restartPollingInterfaceHandler: retrieve active service to poll on interface: " + ipaddr);

            while (rs.next()) {
                serviceIds.add(rs.getInt(1));
            }
            return serviceIds;
        } finally {
            if (rs != null)
                rs.close();
            if (stmt != null)
                stmt.close();
            dbConn.close();
        }
    }

    /**
     * @param ipaddr
     * @return
     * @throws SQLException
     */
    public int getNodeIDForInterface(String ipaddr) throws SQLException {
        Category log = log();

        int nodeid = -1;
        java.sql.Connection dbConn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            // Get datbase connection from the factory
            dbConn = getConnection();

            // Issue query and extract nodeLabel from result set
            stmt = dbConn.createStatement();
            String sql = "SELECT node.nodeid FROM node, ipinterface WHERE ipinterface.ipaddr='" + ipaddr + "' AND ipinterface.nodeid=node.nodeid";
            rs = stmt.executeQuery(sql);
            if (rs.next()) {
                nodeid = rs.getInt(1);
                if (log.isDebugEnabled())
                    log.debug("getNodeLabel: ipaddr=" + ipaddr + " nodeid=" + nodeid);
            }
        } finally {
            try {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            } catch (Exception e) {
                    log.debug("getNodeLabel: an exception occured closing the SQL statement", e);
            }

            // Close the database connection
            if (dbConn != null) {
                try {
                    dbConn.close();
                } catch (Throwable t) {
                    if (log.isDebugEnabled())
                        log.debug("getNodeLabel: an exception occured closing the SQL connection", t);
                }
            }
        }

        return nodeid;
    }

    /**
     * @param nodeId
     * @return
     * @throws SQLException
     */
    public String getNodeLabel(int nodeId) throws SQLException {
        Category log = log();

        String nodeLabel = null;
        java.sql.Connection dbConn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            // Get datbase connection from the factory
            dbConn = getConnection();

            // Issue query and extract nodeLabel from result set
            stmt = dbConn.createStatement();
            rs = stmt.executeQuery("SELECT nodelabel FROM node WHERE nodeid=" + String.valueOf(nodeId));
            if (rs.next()) {
                nodeLabel = (String) rs.getString("nodelabel");
                if (log.isDebugEnabled())
                    log.debug("getNodeLabel: nodeid=" + nodeId + " nodelabel=" + nodeLabel);
            }
        } finally {
            
            try {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            } catch (Exception e) {
                if (log.isDebugEnabled())
                    log.debug("getNodeLabel: an exception occured closing the SQL statement", e);
            }

            // Close the database connection
            if (dbConn != null) {
                try {
                    dbConn.close();
                } catch (Throwable t) {
                    if (log.isDebugEnabled())
                        log.debug("getNodeLabel: an exception occured closing the SQL connection", t);
                }
            }
        }

        return nodeLabel;
    }

    /**
     * @param ipaddr
     * @return
     * @throws SQLException
     */
    public int getServiceCountForInterface(String ipaddr) throws SQLException {
        Category log = log();
        java.sql.Connection dbConn = getConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int count = -1;
        try {
            // Count active services to poll
            stmt = dbConn.prepareStatement(DefaultQueryManager.SQL_COUNT_IFSERVICES_TO_POLL);

            stmt.setString(1, ipaddr);

            rs = stmt.executeQuery();
            while (rs.next()) {
                count = rs.getInt(1);
                if (log.isDebugEnabled())
                    log.debug("restartPollingInterfaceHandler: count active ifservices to poll for interface: " + ipaddr);
            }
        } finally {
            if (rs != null)
                rs.close();
            if (stmt != null)
                stmt.close();
            if (dbConn != null)
                dbConn.close();
        }
        return count;
    }

    /**
     * @param svcName
     * @return
     * @throws SQLException
     */
    public List<IfKey> getInterfacesWithService(String svcName) throws SQLException {
        List<IfKey> ifkeys = new ArrayList<IfKey>();
        Category log = log();
        java.sql.Connection dbConn = getConnection();

        if (log.isDebugEnabled())
            log.debug("scheduleExistingInterfaces: dbConn = " + dbConn + ", svcName = " + svcName);

        PreparedStatement stmt = dbConn.prepareStatement(DefaultQueryManager.SQL_RETRIEVE_INTERFACES);
        stmt.setString(1, svcName); // Service name
        ResultSet rs = stmt.executeQuery();

        // Iterate over result set and schedule each
        // interface/service
        // pair which passes the criteria
        //
        while (rs.next()) {
            IfKey key = new IfKey(rs.getInt(1), rs.getString(2));
            ifkeys.add(key);
        }
        
        //Clean-up
        if (rs != null)
            rs.close();
        if (stmt != null)
            stmt.close();
        if (dbConn != null)
            dbConn.close();
        
        return ifkeys;
    }

    /**
     * @param poller
     * @param nodeId
     * @param ipAddr
     * @param svcName
     * @return
     */
    public Date getServiceLostDate(int nodeId, String ipAddr, String svcName, int serviceId) {
        Category log = ThreadCategory.getInstance(Poller.class);
        log.debug("getting last known status for address: " + ipAddr + " service: " + svcName);

        Date svcLostDate = null;
        // Convert service name to service identifier
        //
        if (serviceId < 0) {
            log.warn("Failed to retrieve service identifier for interface " + ipAddr + " and service '" + svcName + "'");
            return svcLostDate;
        }

        PreparedStatement outagesQuery = null;
        ResultSet outagesResult = null;
        Timestamp regainedDate = null;
        Timestamp lostDate = null;

        Connection dbConn = null;
        try {
            dbConn = getConnection();
            // get the outage information for this service on this ip address
            outagesQuery = dbConn.prepareStatement(DefaultQueryManager.SQL_RETRIEVE_SERVICE_STATUS);

            // add the values for the main query
            outagesQuery.setInt(1, nodeId);
            outagesQuery.setString(2, ipAddr);
            outagesQuery.setInt(3, serviceId);

            // add the values for the subquery
            outagesQuery.setInt(4, nodeId);
            outagesQuery.setString(5, ipAddr);
            outagesQuery.setInt(6, serviceId);

            outagesResult = outagesQuery.executeQuery();

            // if there was a result then the service has been down before,
            if (outagesResult.next()) {
                regainedDate = outagesResult.getTimestamp(1);
                lostDate = outagesResult.getTimestamp(2);
                log.debug("getServiceLastKnownStatus: lostDate: " + lostDate);
            }
            // the service has never been down, need to use current date for
            // both
            else {
                Date currentDate = new Date(System.currentTimeMillis());
                regainedDate = new Timestamp(currentDate.getTime());
                lostDate = new Timestamp(currentDate.getTime());
            }
        } catch (SQLException sqlE) {
            log.error("SQL exception while retrieving last known service status for " + ipAddr + "/" + svcName);
        } finally {
            try {
                if (outagesResult != null)
                    outagesResult.close();
                if (outagesQuery != null)
                    outagesQuery.close();
                if (dbConn != null)
                    dbConn.close();
            } catch (SQLException slqE) {
                // Do nothing
            }
        }

        // Now use retrieved outage times to determine current status
        // of the service. If there was an error and we were unable
        // to retrieve the outage times the default of AVAILABLE will
        // be returned.
        //
        if (lostDate != null) {
            // If the service was never regained then simply
            // assign the svc lost date.
            if (regainedDate == null) {
                svcLostDate = new Date(lostDate.getTime());
                log.debug("getServiceLastKnownStatus: svcLostDate: " + svcLostDate);
            }
        }

        return svcLostDate;
    }
    
    
    public Timestamp convertEventTimeToTimeStamp(String time) {
        try {
            Date date = EventConstants.parseToDate(time);
            Timestamp eventTime = new Timestamp(date.getTime());
            return eventTime;
        } catch (ParseException e) {
            throw new RuntimeException("Invalid date format "+time, e);
        }
    }
    
    public void openOutage(String outageIdSQL, int nodeId, String ipAddr, String svcName, int dbId, String time) {
        
        int attempt = 0;
        boolean notUpdated = true;
        int serviceId = getServiceID(svcName);
        
        while (attempt < 2 && notUpdated) {
            try {
                log().info("openOutage: opening outage for "+nodeId+":"+ipAddr+":"+svcName+" with cause "+dbId+":"+time);
                
                SingleResultQuerier srq = new SingleResultQuerier(getDataSource(), outageIdSQL);
                srq.execute();
                Object outageId = srq.getResult();
                
                if (outageId == null) {
                    throw (new Exception("Null outageId returned from Querier with SQL: "+outageIdSQL));
                }
                
                String sql = "insert into outages (outageId, svcLostEventId, nodeId, ipAddr, serviceId, ifLostService) values ("+outageId+", ?, ?, ?, ?, ?)";
                
                Object values[] = {
                        new Integer(dbId),
                        new Integer(nodeId),
                        ipAddr,
                        new Integer(serviceId),
                        convertEventTimeToTimeStamp(time),
                };

                Updater updater = new Updater(getDataSource(), sql);
                updater.execute(values);
                notUpdated = false;
            } catch (Exception e) {
                if (attempt > 1) {
                    log().fatal("openOutage: Second and final attempt failed opening outage for "+nodeId+":"+ipAddr+":"+svcName, e);
                } else {
                    log().info("openOutage: First attempt failed opening outage for "+nodeId+":"+ipAddr+":"+svcName, e);
                }
            }
            attempt++;
        }
    }

    public void resolveOutage(int nodeId, String ipAddr, String svcName, int dbId, String time) {
        int attempt = 0;
        boolean notUpdated = true;
        
        while (attempt < 2 && notUpdated) {
            try {
                log().info("resolving outage for "+nodeId+":"+ipAddr+":"+svcName+" with resolution "+dbId+":"+time);
                int serviceId = getServiceID(svcName);
                
                String sql = "update outages set svcRegainedEventId=?, ifRegainedService=? where nodeId = ? and ipAddr = ? and serviceId = ? and ifRegainedService is null";
                
                Object values[] = {
                        new Integer(dbId),
                        convertEventTimeToTimeStamp(time),
                        new Integer(nodeId),
                        ipAddr,
                        new Integer(serviceId),
                };

                Updater updater = new Updater(getDataSource(), sql);
                updater.execute(values);
                notUpdated = false;
            } catch (Exception e) {
                if (attempt > 1) {
                    log().fatal("resolveOutage: Second and final attempt failed resolving outage for "+nodeId+":"+ipAddr+":"+svcName, e);
                } else {
                    log().info("resolveOutage: first attempt failed resolving outage for "+nodeId+":"+ipAddr+":"+svcName, e);
                }
            }
            attempt++;
        }
    }
    
    public void reparentOutages(String ipAddr, int oldNodeId, int newNodeId) {
        try {
            log().info("reparenting outages for "+oldNodeId+":"+ipAddr+" to new node "+newNodeId);
            String sql = "update outages set nodeId = ? where nodeId = ? and ipaddr = ?";
            
            Object[] values = {
                    new Integer(newNodeId),
                    new Integer(oldNodeId),
                    ipAddr,
                };

            Updater updater = new Updater(getDataSource(), sql);
            updater.execute(values);
        } catch (Exception e) {
            log().fatal(" Error reparenting outage for "+oldNodeId+":"+ipAddr+" to "+newNodeId, e);
        }
        
    }

    public int getServiceID(String serviceName) {
        if (serviceName == null) return -1;

        SingleResultQuerier querier = new SingleResultQuerier(getDataSource(), "select serviceId from service where serviceName = ?");
        querier.execute(serviceName);
        final Integer result = (Integer)querier.getResult();
        return result == null ? -1 : result.intValue();
    }

    /**
     * Private helper method for getting a Category for logging.
     * 
     * @return A log <code>Category</code>
     */
    private Category log() {
        return ThreadCategory.getInstance(getClass());
    }

    public String[] getCriticalPath(int nodeId) {
        final String[] cpath = new String[2];
        Querier querier = new Querier(getDataSource(), "SELECT criticalpathip, criticalpathservicename FROM pathoutage where nodeid=?") {
    
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                cpath[0] = rs.getString(1);
                cpath[1] = rs.getString(2);
            }
    
        };
        querier.execute(Integer.valueOf(nodeId));
    
        if (cpath[0] == null || cpath[0].equals("")) {
            cpath[0] = OpennmsServerConfigFactory.getInstance().getDefaultCriticalPathIp();
            cpath[1] = "ICMP";
        }
        if (cpath[1] == null || cpath[1].equals("")) {
            cpath[1] = "ICMP";
        }
        return cpath;
    }

}
