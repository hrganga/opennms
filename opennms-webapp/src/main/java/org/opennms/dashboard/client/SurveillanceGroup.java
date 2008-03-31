/*
 * This file is part of the OpenNMS(R) Application.
 *
 * OpenNMS(R) is Copyright (C) 2007 The OpenNMS Group, Inc.  All rights reserved.
 * OpenNMS(R) is a derivative work, containing both original code, included code and modified
 * code that was published under the GNU General Public License. Copyrights for modified
 * and included code are below.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * Modifications:
 * 
 * Created: February 20, 2007
 *
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * For more information contact:
 *      OpenNMS Licensing       <license@opennms.org>
 *      http://www.opennms.org/
 *      http://www.opennms.com/
 */

package org.opennms.dashboard.client;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * 
 * @author <a href="mailto:brozow@opennms.org">Mathew Brozowski</a>
 * @author <a href="mailto:dj@opennms.org">DJ Gregor</a>
 */
public class SurveillanceGroup extends SurveillanceSet implements IsSerializable {
    
    private String m_label;
    private String m_id;
    private boolean m_column;
    
    public SurveillanceGroup() {
        this(null, null, false);
    }
    
    public SurveillanceGroup(String id, String label, boolean isColumn) {
        m_id = id;
        m_label = label;
        m_column = isColumn;
    }

    public String getId() {
        return m_id;
    }

    public String getLabel() {
        return m_label;
    }

    public void setId(String id) {
        m_id = id;
    }

    public void setLabel(String label) {
        m_label = label;
    }

    public boolean isColumn() {
        return m_column;
    }

    public void setColumn(boolean isColumn) {
        m_column = isColumn;
    }
    
    public String toString() {
        return m_label;
    }

    public void visit(Visitor v) {
        v.visitGroup(this);
    }
}
