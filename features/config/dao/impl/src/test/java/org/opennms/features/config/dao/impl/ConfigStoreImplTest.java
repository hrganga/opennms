/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019-2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/
package org.opennms.features.config.dao.impl;

import java.io.IOException;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import org.opennms.features.config.dao.api.*;
import org.opennms.features.config.dao.impl.*;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml",
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/applicationContext-databasePopulator.xml" })
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase
@Transactional
public class ConfigStoreImplTest {
    final String serviceName = "testServiceName";
    final String version = "1.0";
    final String filename = "testFilename";
    @Autowired
    private ConfigStoreDao configStoreDao;

    @Test
    public void testData() throws IOException {
        ConfigData<Map<String, String>> configData = new ConfigData<Map<String, String>>(serviceName, "1.0", Object.class);
        Map<String, String> config = new HashMap<>();
        config.put("test", "test");
        configData.getConfigs().put(filename, config);
        // register
        boolean status = configStoreDao.register(configData);
        Assert.assertTrue("FAIL TO WRITE CONFIG", status);

        //get
        Optional<ConfigData> result = configStoreDao.getConfigData(serviceName);
        Assert.assertTrue("FAIL TO getConfig", result.isPresent());

        //list all
        Optional<List<ConfigData>> all = configStoreDao.getServices();
        Assert.assertEquals("FAIL TO getConfig", all.get().size(), 1 );

        // update
        Map<String, String> config2 = new HashMap<>();
        config2.put("test2", "test2");
        status = configStoreDao.addOrUpdateConfig(serviceName, filename + "_2", config2);
        Assert.assertTrue("FAIL TO addOrUpdateConfig", status);
        Optional<ConfigData> resultAfterUpdate = configStoreDao.getConfigData(serviceName);
        Assert.assertTrue("FAIL TO getConfig", result.isPresent());
        Assert.assertTrue("FAIL configs count is not equal to 2", resultAfterUpdate.get().getConfigs().size() == 2);

        // delete config
        status = configStoreDao.deleteConfig(serviceName, filename + "_2");
        Assert.assertTrue("FAIL TO deleteConfig", true);
        Optional<ConfigData> resultAfterDelete = configStoreDao.getConfigData(serviceName);
        Assert.assertTrue("FAIL configs count is not equal to 1", resultAfterDelete.get().getConfigs().size() == 1);

        // updateConfigs
        status = configStoreDao.updateConfigs(serviceName, new HashMap<>());
        Assert.assertTrue("FAIL TO updateConfigs", true);
        Optional<ConfigData> resultAfterUpdateConfigs = configStoreDao.getConfigData(serviceName);
        Assert.assertTrue("FAIL configs count is not equal to 0", resultAfterUpdateConfigs.get().getConfigs().size() == 0);

        // deregister
        configStoreDao.deregister(serviceName);
        Optional<ConfigData> resultAfterDeregister = configStoreDao.getConfigData(serviceName);
        Assert.assertTrue("FAIL TO deregister config", resultAfterDeregister.isEmpty());
    }
}
