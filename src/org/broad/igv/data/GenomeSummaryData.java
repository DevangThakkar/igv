/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.broad.igv.data;

import org.apache.log4j.Logger;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.tdf.Accumulator;
import org.broad.igv.track.WindowFunction;
import org.broad.igv.util.collections.FloatArrayList;
import org.broad.igv.util.collections.IntArrayList;

import java.util.*;

/**
 * Summarize (using a windowing function) numeric data points which are associated
 * with locations on a genome. Stored by chromosome
 * @author jrobinso
 */
public class GenomeSummaryData {

    private static Logger log = Logger.getLogger(GenomeSummaryData.class);

    // Genome coordinates are in kilobases
    private static final double locationUnit = 1000.0;

    /**
     * Number of virtual pixels
     */
    int nPixels = 1000;

    Genome genome;

    String[] samples;

    /**
     * Chromosome name -> [sample name -> list of data values]
     */
    Map<String, Map<String, FloatArrayList>> dataMap = new HashMap<String, Map<String, FloatArrayList>>();

    /**
     * Chromosome name -> list of start locations
     */
    Map<String, IntArrayList> locationMap;

    /**
     * start locations of currently relevant ordered list of chromosomes, spanning whole genomes
     */
    int[] locations;


    /**
     * sample name -> list of sample data
     */
    Map<String, float[]> data;

    int nDataPts = 0;

    Set<String> skippedChromosomes = new HashSet<String>();


    /**
     * Scale in kilobases / pixel
     */
    double scale;

    public GenomeSummaryData(Genome genome, String[] samples) {
        this.genome = genome;
        this.samples = samples;
        scale = (genome.getNominalLength() / locationUnit) / nPixels;

        List<String> chrNames = genome.getLongChromosomeNames();
        locationMap = new HashMap<String, IntArrayList>();
        dataMap = new HashMap<String, Map<String, FloatArrayList>>();
        for (String chr : chrNames) {
            locationMap.put(chr, new IntArrayList(nPixels / 10));
            dataMap.put(chr, new HashMap<String, FloatArrayList>());
            for (String s : samples) {
                dataMap.get(chr).put(s, new FloatArrayList(nPixels / 10));
            }
        }
    }


    public void addData(String chr, int[] locs, Map<String, float[]> sampleData) {

        IntArrayList locations = locationMap.get(chr);
        if (locations == null) {
            if (!skippedChromosomes.contains(chr)) {
                skippedChromosomes.add(chr);
                log.info("Skipping data for: " + chr);
            }
            return;
        }

        int lastPixel = -1;
        Map<String, Accumulator> dataPoints = new HashMap<String, Accumulator>();

        for (int i = 0; i < locs.length; i++) {

            int genomeLocation = genome.getGenomeCoordinate(chr, locs[i]);
            int pixel = (int) (genomeLocation / scale);
            if (i > 0 && pixel != lastPixel) {
                nDataPts++;

                locations.add(genomeLocation);
                for (String s : dataMap.get(chr).keySet()) {
                    Accumulator dp = dataPoints.get(s);
                    dp.finish();
                    dataMap.get(chr).get(s).add(dp.getValue());
                }
                dataPoints.clear();
            }

            for (String s : samples) {
                float[] data = sampleData.get(s);
                Accumulator dp = dataPoints.get(s);
                if (dp == null) {
                    dp = new Accumulator(WindowFunction.mean);
                    dataPoints.put(s, dp);
                }
                try {
                    dp.add(1, data[i], null);
                } catch (Exception e) {
                    log.error("Error adding to GenomeSummaryData", e);
                }
            }

            lastPixel = pixel;
        }
    }

    public int[] getLocations() {
        if (locations == null) {
            createDataArrays();
        }

        return locations;
    }


    public float[] getData(String sample) {
        if (!data.containsKey(sample)) {
            createDataArrays();
        }
        return data.get(sample);

    }

    /**
     * Recalculate:
     * 0. Start locations for plotting. Shared across all samples
     * 1. Summary data for a given sample, across all stored chromosomes
     */
    private synchronized void createDataArrays() {
        locations = new int[nDataPts];
        int offset = 0;
        List<String> chrNames = genome.getLongChromosomeNames();
        for (String chr : chrNames) {
            int[] chrLocs = locationMap.get(chr).toArray();
            System.arraycopy(chrLocs, 0, locations, offset, chrLocs.length);
            offset += chrLocs.length;
        }

        data = new HashMap<String, float[]>();
        for (String s : samples) {
            float[] sampleData = new float[nDataPts];
            offset = 0;
            for (String chr : chrNames) {
                float[] chrData = dataMap.get(chr).get(s).toArray();
                System.arraycopy(chrData, 0, sampleData, offset, chrData.length);
                offset += chrData.length;
            }
            data.put(s, sampleData);
        }

        locationMap.clear();
        dataMap.clear();
    }

}
