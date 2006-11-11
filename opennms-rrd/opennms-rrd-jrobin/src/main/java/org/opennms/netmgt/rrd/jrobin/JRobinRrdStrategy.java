//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2002-2005 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Modifications:
//
// Jul 8, 2004: Created this file.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.                                                            
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//       
// For more information contact: 
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//
// Tab Size = 8
//

package org.opennms.netmgt.rrd.jrobin;

import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Category;
import org.apache.log4j.Priority;
import org.jrobin.core.FetchData;
import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDef;
import org.jrobin.core.RrdException;
import org.jrobin.core.Sample;
import org.jrobin.graph.RrdGraph;
import org.jrobin.graph.RrdGraphDef;
import org.opennms.core.utils.StringUtils;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.rrd.RrdDataSource;
import org.opennms.netmgt.rrd.RrdStrategy;
import org.opennms.netmgt.rrd.RrdUtils;

/**
 * Provides a JRobin based implementation of RrdStrategy. It uses JRobin 1.4 in
 * FILE mode (NIO is too memory consuming for the large number of files that we
 * open)
 */
public class JRobinRrdStrategy implements RrdStrategy {

    private boolean m_initialized = false;

    /**
     * Closes the JRobin RrdDb.
     */
    public void closeFile(Object rrdFile) throws Exception {
        ((RrdDb) rrdFile).close();
    }

    public Object createDefinition(String creator, String directory, String rrdName, int step, List dataSources, List rraList) throws Exception {
        File f = new File(directory);
        f.mkdirs();

        String fileName = directory + File.separator + rrdName + RrdUtils.getExtension();
        
        if (new File(fileName).exists())
            return null;

        RrdDef def = new RrdDef(fileName);

        // def.setStartTime(System.currentTimeMillis()/1000L - 2592000L);
        def.setStartTime(1000);
        def.setStep(step);
        
        for (Iterator iter = dataSources.iterator(); iter.hasNext();) {
            RrdDataSource dataSource = (RrdDataSource) iter.next();
            String dsMin = dataSource.getMin();
            String dsMax = dataSource.getMax();
            double min = (dsMin == null || "U".equals(dsMin) ? Double.NaN : Double.parseDouble(dsMin));
            double max = (dsMax == null || "U".equals(dsMax) ? Double.NaN : Double.parseDouble(dsMax));
            def.addDatasource(dataSource.getName(), dataSource.getType(), dataSource.getHeartBeat(), min, max);
        }

        for (Iterator it = rraList.iterator(); it.hasNext();) {
            String rra = (String) it.next();
            def.addArchive(rra);
        }

        return def;
    }


    /**
     * Creates the JRobin RrdDb from the def by opening the file and then
     * closing. TODO: Change the interface here to create the file and return it
     * opened.
     */
    public void createFile(Object rrdDef) throws Exception {
        if (rrdDef == null) return;
        
        RrdDb rrd = new RrdDb((RrdDef) rrdDef);
        rrd.close();
    }

    /**
     * Opens the JRobin RrdDb by name and returns it.
     */
    public Object openFile(String fileName) throws Exception {
        RrdDb rrd = new RrdDb(fileName);
        return rrd;
    }

    /**
     * Creates a sample from the JRobin RrdDb and passes in the data provided.
     */
    public void updateFile(Object rrdFile, String owner, String data) throws Exception {
        Sample sample = ((RrdDb) rrdFile).createSample();
        sample.setAndUpdate(data);
    }

    /**
     * Initialized the RrdDb to use the FILE factory because the NIO factory
     * uses too much memory for our implementation.
     */
    public synchronized void initialize() throws Exception {
        if (!m_initialized) {
            RrdDb.setDefaultFactory("FILE");
            m_initialized = true;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opennms.netmgt.rrd.RrdStrategy#graphicsInitialize()
     */
    public void graphicsInitialize() throws Exception {
        initialize();
    }

    /**
     * Fetch the last value from the JRobin RrdDb file.
     */
    public Double fetchLastValue(String fileName, int interval) throws NumberFormatException, org.opennms.netmgt.rrd.RrdException {
        RrdDb rrd = null;
        try {
            long now = System.currentTimeMillis();
            long collectTime = (now - (now % interval)) / 1000L;
            rrd = new RrdDb(fileName);
            FetchData data = rrd.createFetchRequest("AVERAGE", collectTime, collectTime).fetchData();
            double[] vals = data.getValues(0);
            if (vals.length > 0) {
                return new Double(vals[vals.length - 1]);
            }
            return null;
        } catch (IOException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } catch (RrdException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } finally {
            if (rrd != null)
                try {
                    rrd.close();
                } catch (IOException e) {
                    Category log = ThreadCategory.getInstance(getClass());
                    log.error("Failed to close rrd file: " + fileName, e);
                }
        }
    }
    
    public Double fetchLastValueInRange(String fileName, int interval, int range) throws NumberFormatException, org.opennms.netmgt.rrd.RrdException {
        RrdDb rrd = null;
        try {
        	Category log = ThreadCategory.getInstance(getClass());
        	rrd = new RrdDb(fileName);
         	long now = System.currentTimeMillis();
            long latestUpdateTime = (now - (now % interval)) / 1000L;
            long earliestUpdateTime = ((now - (now % interval)) - range) / 1000L;
            if (log.isEnabledFor(Priority.DEBUG))
            	log.debug("fetchInRange: fetching data from " + earliestUpdateTime + " to " + latestUpdateTime);
            
            FetchData data = rrd.createFetchRequest("AVERAGE", earliestUpdateTime, latestUpdateTime).fetchData();
            
		    double[] vals = data.getValues(0);
		    long[] times = data.getTimestamps();
		    
		    // step backwards through the array of values until we get something that's a number
            
		    for(int i = vals.length - 1; i >= 0; i--) {
            	if ( Double.isNaN(vals[i]) ) {
            		if (log.isEnabledFor(Priority.DEBUG))
            			log.debug("fetchInRange: Got a NaN value at interval: " + times[i] + " continuing back in time");
            	} else {
               		if (log.isEnabledFor(Priority.DEBUG))
               			log.debug("Got a non NaN value at interval: " + times[i] + " : " + vals[i] );
            		return new Double(vals[i]);
               	}
            }
            return null;
        } catch (IOException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } catch (RrdException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } finally {
            if (rrd != null)
                try {
                    rrd.close();
                } catch (IOException e) {
                    Category log = ThreadCategory.getInstance(getClass());
                    log.error("Failed to close rrd file: " + fileName, e);
                }
        }
    }

    private Color getColor(String colorValue) {
        int colorVal = Integer.parseInt(colorValue, 16);
        return new Color(colorVal);
    }

    /**
     * This constructs a graphDef by parsing the rrdtool style command and using
     * the values to create the JRobin graphDef. It does not understand the 'AT
     * style' time arguments however. Also there may be some rrdtool parameters
     * that it does not understand. These will be ignored. The graphDef will be
     * used to construct an RrdGraph and a PNG image will be created. An input
     * stream returning the bytes of the PNG image is returned.
     */
    public InputStream createGraph(String command, File workDir) throws IOException, org.opennms.netmgt.rrd.RrdException {
        Category log = ThreadCategory.getInstance(getClass());
        try {
            InputStream tempIn = null;
            String[] commandArray = tokenize(command, " \t", false);

            RrdGraphDef graphDef = new RrdGraphDef();
            long start = 0;
            long end = 0;
            int height = 100;
            int width = 400;
            double lowerLimit = Double.NaN;
            double upperLimit = Double.NaN;
            boolean rigid = false;
            for (int i = 0; i < commandArray.length; i++) {
                String arg = commandArray[i];
                if (arg.startsWith("--start=")) {
                    start = Long.parseLong(arg.substring("--start=".length()));
                    log.debug("JRobin start time: " + start);
                } else if (arg.equals("--start")) {
                    if (i + 1 < commandArray.length) {
                        start = Long.parseLong(commandArray[++i]);
                        log.debug("JRobin start time: " + start);
                    } else {
                        throw new IllegalArgumentException("--start must be followed by a start time");
                    }
                    
                } else if (arg.startsWith("--end=")) {
                    end = Long.parseLong(arg.substring("--end=".length()));
                    log.debug("JRobin end time: " + end);
                } else if (arg.equals("--end")) {
                    if (i + 1 < commandArray.length) {
                        end = Long.parseLong(commandArray[++i]);
                        log.debug("JRobin end time: " + start);
                    } else {
                        throw new IllegalArgumentException("--end must be followed by an end time");
                    }
                    
                } else if (arg.startsWith("--title=")) {
                    String[] title = tokenize(arg, "=", true);
                    graphDef.setTitle(title[1]);
                } else if (arg.equals("--title")) {
                    if (i + 1 < commandArray.length) {
                        graphDef.setTitle(commandArray[++i]);
                    } else {
                        throw new IllegalArgumentException("--title must be followed by a title");
                    }
                    
                } else if (arg.startsWith("--color=")) {
                    String[] color = tokenize(arg, "=", true);
                    parseGraphColor(graphDef, color[1]);
                } else if (arg.equals("--color") || arg.equals("-c")) {
                    if (i + 1 < commandArray.length) {
                        parseGraphColor(graphDef, commandArray[++i]);
                    } else {
                        throw new IllegalArgumentException("--color must be followed by a color");
                    }
                    
                } else if (arg.startsWith("--vertical-label=")) {
                    String[] label = tokenize(arg, "=", true);
                    graphDef.setVerticalLabel(label[1]);
                } else if (arg.equals("--vertical-label")) {
                    if (i + 1 < commandArray.length) {
                        graphDef.setVerticalLabel(commandArray[++i]);
                    } else {
                        throw new IllegalArgumentException("--vertical-label must be followed by a label");
                    }
                    
                } else if (arg.startsWith("--height=")) {
                    String[] argParm = tokenize(arg, "=", true);
                    height = Integer.parseInt(argParm[1]);
                    log.debug("JRobin height: "+height);
                } else if (arg.equals("--height")) {
                    if (i + 1 < commandArray.length) {
                        height = Integer.parseInt(commandArray[++i]);
                        log.debug("JRobin height: "+height);
                    } else {
                        throw new IllegalArgumentException("--height must be followed by a number");
                    }
                
                } else if (arg.startsWith("--width=")) {
                    String[] argParm = tokenize(arg, "=", true);
                    width = Integer.parseInt(argParm[1]);
                    log.debug("JRobin width: "+height);
                } else if (arg.equals("--width")) {
                    if (i + 1 < commandArray.length) {
                        width = Integer.parseInt(commandArray[++i]);
                        log.debug("JRobin width: "+height);
                    } else {
                        throw new IllegalArgumentException("--width must be followed by a number");
                    }
                
                } else if (arg.startsWith("--units-exponent=")) {
                    String[] argParm = tokenize(arg, "=", true);
                    int exponent = Integer.parseInt(argParm[1]);
                    log.debug("JRobin units exponent: "+exponent);
                    graphDef.setUnitsExponent(exponent);
                } else if (arg.equals("--units-exponent")) {
                    if (i + 1 < commandArray.length) {
                        int exponent = Integer.parseInt(commandArray[++i]);
                        log.debug("JRobin units exponent: "+exponent);
                        graphDef.setUnitsExponent(exponent);
                    } else {
                        throw new IllegalArgumentException("--units-exponent must be followed by a number");
                    }
                
                } else if (arg.startsWith("--lower-limit=")) {
                    String[] argParm = tokenize(arg, "=", true);
                    lowerLimit = Double.parseDouble(argParm[1]);
                    log.debug("JRobin lower limit: "+lowerLimit);
                } else if (arg.equals("--lower-limit")) {
                    if (i + 1 < commandArray.length) {
                        lowerLimit = Double.parseDouble(commandArray[++i]);
                        log.debug("JRobin lower limit: "+lowerLimit);
                    } else {
                        throw new IllegalArgumentException("--lower-limit must be followed by a number");
                    }
                
                } else if (arg.startsWith("--upper-limit=")) {
                    String[] argParm = tokenize(arg, "=", true);
                    upperLimit = Double.parseDouble(argParm[1]);
                    log.debug("JRobin upp limit: "+lowerLimit);
                } else if (arg.equals("--upper-limit")) {
                    if (i + 1 < commandArray.length) {
                        upperLimit = Double.parseDouble(commandArray[++i]);
                        log.debug("JRobin upper limit: "+lowerLimit);
                    } else {
                        throw new IllegalArgumentException("--upper-limit must be followed by a number");
                    }
                
                } else if (arg.equals("--rigid")) {
                    rigid = true;
                
                } else if (arg.startsWith("DEF:")) {
                    String definition = arg.substring("DEF:".length());
                    String[] def = tokenize(definition, ":", true);
                    String[] ds = tokenize(def[0], "=", true);
                    File dsFile = new File(workDir, ds[1]);
                    graphDef.datasource(ds[0], dsFile.getAbsolutePath(), def[1], def[2]);
                
                } else if (arg.startsWith("CDEF:")) {
                    String definition = arg.substring("CDEF:".length());
                    String[] cdef = tokenize(definition, "=", true);
                    graphDef.datasource(cdef[0], cdef[1]);
                
                } else if (arg.startsWith("LINE1:")) {
                    String definition = arg.substring("LINE1:".length());
                    String[] line1 = tokenize(definition, ":", true);
                    String[] color = tokenize(line1[0], "#", true);
                    graphDef.line(color[0], getColor(color[1]), (line1.length > 1 ? line1[1] : ""));
                
                } else if (arg.startsWith("LINE2:")) {
                    String definition = arg.substring("LINE2:".length());
                    String[] line2 = tokenize(definition, ":", true);
                    String[] color = tokenize(line2[0], "#", true);
                    graphDef.line(color[0], getColor(color[1]), (line2.length > 1 ? line2[1] : ""), 2);

                } else if (arg.startsWith("LINE3:")) {
                    String definition = arg.substring("LINE3:".length());
                    String[] line3 = tokenize(definition, ":", true);
                    String[] color = tokenize(line3[0], "#", true);
                    graphDef.line(color[0], getColor(color[1]), (line3.length > 1 ? line3[1] : ""), 3);

                } else if (arg.startsWith("GPRINT:")) {
                    String definition = arg.substring("GPRINT:".length());
                    String gprint[] = tokenize(definition, ":", true);
                    String format = gprint[2];
                    //format = format.replaceAll("%(\\d*\\.\\d*)lf", "@$1");
                    //format = format.replaceAll("%s", "@s");
                    //format = format.replaceAll("%%", "%");
                    //log.debug("gprint: oldformat = " + gprint[2] + " newformat = " + format);
                    format = format.replaceAll("\\n", "\n");
                    graphDef.gprint(gprint[0], gprint[1], format);

                } else if (arg.startsWith("COMMENT:")) {
                    String comments[] = tokenize(arg, ":", true);
                    String format = comments[1].replaceAll("\\n", "\n");
                    graphDef.comment(format);
                } else if (arg.startsWith("AREA:")) {
                    String definition = arg.substring("AREA:".length());
                    String area[] = tokenize(definition, ":", true);
                    String[] color = tokenize(area[0], "#", true);
                    graphDef.area(color[0], getColor(color[1]), (area.length > 1 ? area[1] : ""));

                } else if (arg.startsWith("STACK:")) {
                    String definition = arg.substring("STACK:".length());
                    String stack[] = tokenize(definition, ":", true);
                    String[] color = tokenize(stack[0], "#", true);
                    graphDef.stack(color[0], getColor(color[1]), (stack.length > 1 ? stack[1] : ""));
                
                } else {
                    log.warn("JRobin: Unrecognized graph argument: " + arg);
                }
            }
            graphDef.setTimeSpan(start, end);
            graphDef.setMinValue(lowerLimit);
            graphDef.setMaxValue(upperLimit);
            graphDef.setRigid(rigid);
            graphDef.setHeight(height);
            graphDef.setWidth(width);
            graphDef.setSmallFont(new Font("Monospaced", Font.PLAIN, 10));
            graphDef.setLargeFont(new Font("Monospaced", Font.PLAIN, 12));

            log.debug("JRobin Finished tokenizing checking: start time: " + start + "; end time: " + end);

            RrdGraph graph = new RrdGraph(graphDef);

            byte[] bytes = graph.getRrdGraphInfo().getBytes();

            tempIn = new ByteArrayInputStream(bytes);

            return tempIn;
        } catch (Exception e) {
            log.error("JRobin:exception occurred creating graph: " + e.getMessage(), e);
            throw new org.opennms.netmgt.rrd.RrdException("An exception occurred creating the graph: " + e.getMessage(), e);
        }
    }
    
    private String[] tokenize(String line, String delimiters, boolean processQuotes) {
        return StringUtils.tokenizeWithQuotingAndEscapes(line, delimiters, processQuotes);
    }

    /**
     * @param colorArg Should have the form COLORTAG#RRGGBB
     * @see http://www.jrobin.org/support/man/rrdgraph.html
     */
    private void parseGraphColor(RrdGraphDef graphDef, String colorArg) throws IllegalArgumentException
    {
        // Parse for format COLORTAG#RRGGBB
        String[] colorArgParts = tokenize(colorArg, "#", false);
        if (colorArgParts.length != 2)
            throw new IllegalArgumentException
                 ("--color must be followed by value with format "
                  + "COLORTAG#RRGGBB");

        String colorTag = colorArgParts[0].toUpperCase();
        String colorHex = colorArgParts[1].toUpperCase();

        // validate hex color input is actually an RGB hex color value
        if (colorHex.length() != 6)
            throw new IllegalArgumentException
                 ("--color must be followed by value with format "
                  + "COLORTAG#RRGGBB");

        // this might throw NumberFormatException, but whoever wrote
        // createGraph didn't seem to care, so I guess I don't care either.
        // It'll get wrapped in an RrdException anyway.
        Color color = getColor(colorHex);

        // These are the documented RRD color tags
	try {
        if (colorTag.equals("BACK")) {
            graphDef.setColor("BACK", color);
        }
        else if (colorTag.equals("CANVAS")) {
            graphDef.setColor("CANVAS", color);
        }
        else if (colorTag.equals("SHADEA")) {
            graphDef.setColor("SHADEA", color);
        }
        else if (colorTag.equals("SHADEB")) {
            graphDef.setColor("SHADEB", color);
        }
        else if (colorTag.equals("GRID")) {
            graphDef.setColor("GRID", color);
        }
        else if (colorTag.equals("MGRID")) {
            graphDef.setColor("MGRID", color);
        }
        else if (colorTag.equals("FONT")) {
            graphDef.setColor("FONT", color);
        }
        else if (colorTag.equals("FRAME")) {
            graphDef.setColor("FRAME", color);
        }
        else if (colorTag.equals("ARROW")) {
            graphDef.setColor("ARROW", color);
        }
        else {
            throw new org.jrobin.core.RrdException
                 ("Unknown color tag " + colorTag);
        }
        } catch (Exception e) {
	    Category log = ThreadCategory.getInstance(getClass());
            log.error("JRobin:exception occurred creating graph", e);
        }
    }

    /**
     * This implementation does not track and stats.
     */
    public String getStats() {
        return "";
    }

    public int getGraphRightOffset() {
        return -22;
    }

    public int getGraphTopOffsetWithText() {
        return -72;
    }

}
