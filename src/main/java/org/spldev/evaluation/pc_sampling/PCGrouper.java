/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evaluation of PC-Sampling.
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

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.spldev.evaluation.Evaluator;
import org.spldev.evaluation.pc_sampling.eval.Constants;
import org.spldev.evaluation.pc_sampling.eval.Expressions;
import org.spldev.evaluation.pc_sampling.eval.Grouper;
import org.spldev.evaluation.pc_sampling.eval.properties.GroupingProperty;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.clauses.CNF;
import org.spldev.formula.clauses.ClauseList;
import org.spldev.formula.clauses.Clauses;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.io.DIMACSFormat;
import org.spldev.util.Result;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.io.format.FormatSupplier;
import org.spldev.util.logging.Logger;

public class PCGrouper extends Evaluator {

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("Configuration path and name not specified!");
			return;
		}
		final PCGrouper evaluator = new PCGrouper(args[0], args[1]);
		evaluator.init();
		evaluator.run();
		evaluator.dispose();
	}

	protected CSVWriter groupingWriter;

	public PCGrouper(String configPath, String configName) throws Exception {
		super(configPath, configName);
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		groupingWriter = addCSVWriter("grouping.csv",
				Arrays.asList("ID", "Mode", "Iteration", "Time", "Size", "Error"));
	}

	@Override
	public void run() {
		super.run();

		tabFormatter.setTabLevel(0);
		if (config.systemIterations.getValue() > 0) {
			Logger.logInfo("Start");
			tabFormatter.incTabLevel();

			final int systemIndexEnd = config.systemNames.size();
			for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
				logSystem();
				tabFormatter.incTabLevel();
				final String systemName = config.systemNames.get(systemIndex);

				final ModelReader<Formula> fmReader = new ModelReader<>();
				fmReader.setPathToFiles(config.modelPath);
				fmReader.setFormatSupplier(FormatSupplier.of(new DIMACSFormat()));
				final Result<CNF> fm = fmReader.read(systemName).map(Clauses::convertToCNF);
				if (fm.isEmpty()) {
					Logger.logInfo("No feature model!");
				}
				final CNF cnf = fm.get();

				try {
					if (cnf != null) {
						final Grouper pcfmProcessor = new Grouper(cnf, systemName);
						evalGroup(GroupingProperty.FM_ONLY, pcfmProcessor);
						evalGroup(GroupingProperty.PC_ALL_FM, pcfmProcessor);
						evalGroup(GroupingProperty.PC_ALL_FM_FM, pcfmProcessor);
						evalGroup(GroupingProperty.PC_FOLDER_FM, pcfmProcessor);
						evalGroup(GroupingProperty.PC_FILE_FM, pcfmProcessor);
						evalGroup(GroupingProperty.PC_VARS_FM, pcfmProcessor);
					}

					final Grouper pcProcessor = new Grouper(null, systemName);
					evalGroup(GroupingProperty.PC_ALL, pcProcessor);
					evalGroup(GroupingProperty.PC_FOLDER, pcProcessor);
					evalGroup(GroupingProperty.PC_FILE, pcProcessor);
					evalGroup(GroupingProperty.PC_VARS, pcProcessor);
				} catch (final Exception e) {
					e.printStackTrace();
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

	private Expressions evalGroup(String groupingValue, final Grouper pcProcessor) {
		Expressions expressions = null;
		for (int i = 0; i < config.systemIterations.getValue(); i++) {
			groupingWriter.createNewLine();
			try {
				groupingWriter.addValue(config.systemIDs.get(systemIndex));
				groupingWriter.addValue(groupingValue);
				groupingWriter.addValue(i);

				final long localTime = System.nanoTime();
				expressions = pcProcessor.group(groupingValue);
				final long timeNeeded = System.nanoTime() - localTime;

				groupingWriter.addValue(timeNeeded);

				if (expressions != null) {
					final HashSet<ClauseList> pcs = new HashSet<>();
					for (final List<ClauseList> group : expressions.getExpressions()) {
						pcs.addAll(group);
					}
					groupingWriter.addValue(pcs.size());
					groupingWriter.addValue(false);
				} else {
					groupingWriter.addValue(0);
					groupingWriter.addValue(true);
				}

				Logger.logInfo(groupingValue + " -> " + Double.toString((timeNeeded / 1_000_000) / 1_000.0));
			} catch (final FileNotFoundException e) {
				groupingWriter.removeLastLine();
			} catch (final Exception e) {
				groupingWriter.removeLastLine();
				Logger.logError(e);
			} finally {
				groupingWriter.flush();
			}
		}

		if (expressions != null) {
			Expressions.writeConditions(expressions, config.systemNames.get(systemIndex),
					Constants.groupedPCFileName + groupingValue);
			Logger.logInfo(Constants.groupedPCFileName + groupingValue + " OK");
		} else {
			Logger.logInfo(Constants.groupedPCFileName + groupingValue + " FAIL");
		}
		return expressions;
	}

}
