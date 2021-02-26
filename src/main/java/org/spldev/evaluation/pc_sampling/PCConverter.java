/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evalaution of PC-Sampling.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-PC-Sampling.
 * 
 * Evaluation-PC-Sampling is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-PC-Sampling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-PC-Sampling.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-pc-sampling> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.pc_sampling;

import java.util.Arrays;

import org.spldev.evaluation.Evaluator;
import org.spldev.evaluation.pc_sampling.eval.Constants;
import org.spldev.evaluation.pc_sampling.eval.Converter;
import org.spldev.evaluation.pc_sampling.eval.analyzer.PresenceCondition;
import org.spldev.evaluation.pc_sampling.eval.analyzer.PresenceConditionList;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.io.DIMACSFormat;
import org.spldev.util.Result;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.io.format.FormatSupplier;
import org.spldev.util.logging.Logger;

public class PCConverter extends Evaluator {

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("Configuration path and name not specified!");
			return;
		}
		final PCConverter evaluator = new PCConverter(args[0], args[1]);
		evaluator.init();
		evaluator.run();
		evaluator.dispose();
	}

	protected CSVWriter conversionWriter;

	public PCConverter(String configPath, String configName) throws Exception {
		super(configPath, configName);
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		conversionWriter = addCSVWriter("conversion.csv",
				Arrays.asList("ID", "Mode", "Iteration", "Time", "Size", "Error", "Clauses", "Literals"));
	}

	@Override
	public void run() {
		super.run();

		tabFormatter.setTabLevel(0);
		if (config.systemIterations.getValue() > 0) {
			Logger.logInfo("Start");
			tabFormatter.incTabLevel();
			final int systemIndexEnd = config.systemNames.size();
			for (systemID = 0; systemID < systemIndexEnd; systemID++) {
				logSystem();
				tabFormatter.incTabLevel();
				final String systemName = config.systemNames.get(systemID);

				final ModelReader<CNF> fmReader = new ModelReader<>();
				fmReader.setPathToFiles(config.modelPath);
				fmReader.setFormatSupplier(FormatSupplier.of(new DIMACSFormat()));
				final Result<CNF> fm = fmReader.read(systemName);
				if (fm.isEmpty()) {
					Logger.logInfo("No feature model!");
				}
				final CNF cnf = fm.get();
//						.getElement(new NoAbstractCNFCreator()).normalize();
//				CNFSlicer slicer = new CNFSlicer(null);

				try {
					if (cnf != null) {
						final Converter pcfmProcessor = new Converter(cnf, systemName);
						evalConvert(pcfmProcessor, Constants.convertedPCFMFileName);
					} else {
						final Converter pcfmProcessor = new Converter(null, systemName);
						evalConvert(pcfmProcessor, Constants.convertedPCFileName);
					}
				} catch (final Exception e) {
					Logger.logError(e);
				}
				tabFormatter.decTabLevel();
			}
			tabFormatter.setTabLevel(0);
			Logger.logInfo("Finished");
		} else {
			Logger.logInfo("Nothing to do");
		}
	}

	private PresenceConditionList evalConvert(final Converter pcProcessor, String fileName) {
		PresenceConditionList pcList = null;
		for (int i = 0; i < config.systemIterations.getValue(); i++) {
			conversionWriter.createNewLine();
			try {
				conversionWriter.addValue(config.systemIDs.get(systemID));
				conversionWriter.addValue(fileName);
				conversionWriter.addValue(i);

				final long localTime = System.nanoTime();
				pcList = pcProcessor.convert();
				final long timeNeeded = System.nanoTime() - localTime;

				conversionWriter.addValue(timeNeeded);
				if (pcList != null) {
					long countClauses = 0;
					long countLiterals = 0;
					for (final PresenceCondition pc : pcList) {
						final CNF dnf = pc.getDnf();
						countClauses += dnf.getClauses().size();
						for (final LiteralList clause : dnf.getClauses()) {
							countLiterals += clause.size();
						}
					}
					conversionWriter.addValue(pcList.size());
					conversionWriter.addValue(false);
					conversionWriter.addValue(countClauses);
					conversionWriter.addValue(countLiterals);
				} else {
					conversionWriter.addValue(0);
					conversionWriter.addValue(true);
					conversionWriter.addValue(0);
					conversionWriter.addValue(0);
				}

				Logger.logInfo("convert -> " + Double.toString((timeNeeded / 1_000_000) / 1_000.0));
			} catch (final Exception e) {
				conversionWriter.removeLastLine();
				Logger.logError(e);
			} finally {
				conversionWriter.flush();
			}
		}

		if (pcList != null) {
			PresenceConditionList.writePCList(pcList, config.systemNames.get(systemID), fileName);
		}
		return pcList;
	}

}
