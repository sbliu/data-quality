// ============================================================================
//
// Copyright (C) 2006-2021 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.dataquality.record.linkage;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * created by scorreia on Jan 21, 2013 Detailled comment
 * 
 */
public class Messages {

    private static final String BUNDLE_NAME = "messages"; //$NON-NLS-1$

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private static final Logger LOG = LoggerFactory.getLogger(Messages.class);

    private Messages() {
    }

    public static String getString(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            LOG.warn(e.getMessage(), e);
            return '!' + key + '!';
        }

    }

    public static String getString(String key, Object... args) {
        try {
            return MessageFormat.format(getString(key).replaceAll("'", "''"), args); //$NON-NLS-1$//$NON-NLS-2$
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            return "!!!" + key + "!!!"; //$NON-NLS-1$//$NON-NLS-2$
        }
    }

}
