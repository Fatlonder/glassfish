/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */


package org.glassfish.osgijavaeebase;

import org.osgi.framework.*;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This extender is responsible for detecting and deploying any Java EE OSGi bundle.
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
public class JavaEEExtender implements Extender {
    /*
     * Implementation Note: All methods are synchronized, because we don't allow the extender to stop while it
     * is deploying or undeploying something. Similarly, while it is being stopped, we don't want it to deploy
     * or undeploy something.
     * After receiving the event, it spwans a separate thread to carry out the task so that we don't
     * spend long time in the synchronous event listener. More over, that can lead to deadlocks as observed
     * in https://glassfish.dev.java.net/issues/show_bug.cgi?id=14313.
     */

    private OSGiContainer c;
    private static final Logger logger =
            Logger.getLogger(JavaEEExtender.class.getPackage().getName());
    private BundleContext context;
    private ServiceRegistration reg;
    private BundleTracker tracker;
    private ExecutorService executorService;

    public JavaEEExtender(BundleContext context) {
        this.context = context;
    }

    public synchronized void start() {
        executorService = Executors.newSingleThreadExecutor();
        c = new OSGiContainer(context);
        c.init();
        reg = context.registerService(OSGiContainer.class.getName(), c, null);
        tracker = new BundleTracker(context, Bundle.ACTIVE | Bundle.STARTING, new HybridBundleTrackerCustomizer());
        tracker.open();
    }

    public synchronized void stop() {
        if (c == null) return;
        c.shutdown();
        c = null;
        if (tracker != null) tracker.close();
        tracker = null;
        reg.unregister();
        reg = null;
        executorService.shutdownNow();
    }

    private void handleEvent(BundleEvent event) {
        Bundle bundle = event.getBundle();
        switch (event.getType()) {
            case BundleEvent.STARTED:
                // A bundle with LAZY_ACTIVATION policy can be started with eager activation policy unless
                // START_ACTIVATION_POLICY is set in the options while calling bundle.start(int options).
                // So, we can't rely on LAZY_ACTIVATION event alone to deploy a bundle with lazy activation policy.
                // At the same time, if a bundle with lazy activation policy is indeed started lazily, then
                // we would have deployed the bundle upon receiving the LAZY_ACTIVATION event in which case we must
                // avoid duplicate deployment upon receiving STARTED event. Hopefully this explains why we check
                // c.isDeployed(bundle).
                if (!c.isDeployed(bundle)) {
                    deploy(bundle);
                }
                break;
            case BundleEvent.LAZY_ACTIVATION:
                deploy(bundle);
                break;
            case BundleEvent.STOPPED:
                undeploy(bundle);
                break;
        }
    }

    private synchronized void deploy(Bundle b) {
        if (!isStarted()) return;
        try {
            c.deploy(b);
        }
        catch (Exception e) {
            logger.logp(Level.SEVERE, "JavaEEExtender", "deploy",
                    "Exception deploying bundle {0}",
                    new Object[]{b.getLocation()});
            logger.logp(Level.SEVERE, "JavaEEExtender", "deploy",
                    "Exception Stack Trace", e);
        }
    }

    private synchronized void undeploy(Bundle b) {
        if (!isStarted()) return;
        try {
            if (c.isDeployed(b)) {
                c.undeploy(b);
            }
        }
        catch (Exception e) {
            logger.logp(Level.SEVERE, "JavaEEExtender", "undeploy",
                    "Exception undeploying bundle {0}",
                    new Object[]{b.getLocation()});
            logger.logp(Level.SEVERE, "JavaEEExtender", "undeploy",
                    "Exception Stack Trace", e);
        }
    }

    private synchronized boolean isStarted() {
        return c!= null;
    }

    private class HybridBundleTrackerCustomizer implements BundleTrackerCustomizer {
        public Object addingBundle(final Bundle bundle, BundleEvent event) {
            final int state = bundle.getState();
            if (isReady(event, state)) {
                executorService.submit(new Runnable() {
                    public void run() {
                        deploy(bundle);
                    }
                });
                return bundle;
            }
            return null;
        }

        /**
         * Bundle is ready when its state is ACTIVE or, when a lazy activation policy is used, STARTING
         * @param event
         * @param state
         * @return
         */
        private boolean isReady(BundleEvent event, int state) {
            return state == Bundle.ACTIVE ||
                    (state == Bundle.STARTING && (event != null && event.getType() == BundleEvent.LAZY_ACTIVATION));
        }

        public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
        }

        public void removedBundle(final Bundle bundle, BundleEvent event, Object object) {
            executorService.submit(new Runnable() {
                public void run() {
                    undeploy(bundle);
                }
            });
        }
    }
}
