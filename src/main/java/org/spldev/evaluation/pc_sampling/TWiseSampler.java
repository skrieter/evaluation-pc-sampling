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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import org.spldev.evaluation.pc_sampling.algorithms.Chvatal;
import org.spldev.evaluation.pc_sampling.algorithms.Dummy;
import org.spldev.evaluation.pc_sampling.algorithms.FIDEChvatal;
import org.spldev.evaluation.pc_sampling.algorithms.FIDEICPL;
import org.spldev.evaluation.pc_sampling.algorithms.FIDERandom;
import org.spldev.evaluation.pc_sampling.algorithms.ICPL;
import org.spldev.evaluation.pc_sampling.algorithms.IncLing;
import org.spldev.evaluation.pc_sampling.algorithms.PLEDGE_MAX;
import org.spldev.evaluation.pc_sampling.algorithms.PLEDGE_MIN;
import org.spldev.evaluation.pc_sampling.algorithms.YASA;
import org.spldev.evaluation.pc_sampling.eval.Constants;
import org.spldev.evaluation.pc_sampling.eval.Expressions;
import org.spldev.evaluation.pc_sampling.eval.analyzer.PresenceConditionList;
import org.spldev.evaluation.pc_sampling.eval.properties.AlgorithmProperty;
import org.spldev.evaluation.pc_sampling.eval.properties.GroupingProperty;
import org.spldev.evaluation.process.Algorithm;
import org.spldev.evaluation.properties.ListProperty;
import org.spldev.evaluation.properties.Property;
import org.spldev.evaluation.util.ModelReader;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.ClauseList;
import org.spldev.formula.clause.Clauses;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.LiteralList.Order;
import org.spldev.formula.clause.SolutionList;
import org.spldev.formula.clause.analysis.CountSolutionsAnalysis;
import org.spldev.formula.clause.io.DIMACSFormatCNF;
import org.spldev.formula.clause.io.ExpressionGroupFormat;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.io.DIMACSFormat;
import org.spldev.util.Result;
import org.spldev.util.io.FileHandler;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.io.format.FormatSupplier;
import org.spldev.util.job.Executor;
import org.spldev.util.logging.Logger;

public class TWiseSampler extends AlgorithmEvaluator<SolutionList, Algorithm<SolutionList>> {

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("Configuration path and name not specified!");
			return;
		}
		final TWiseSampler evaluator = new TWiseSampler(args[0], args[1]);
		evaluator.init();
		evaluator.run();
		evaluator.dispose();
	}

	protected static final AlgorithmProperty algorithmsProperty = new AlgorithmProperty();
	protected static final ListProperty<String> tProperty = new ListProperty<>("t", Property.StringConverter);
	protected static final ListProperty<String> mProperty = new ListProperty<>("m", Property.StringConverter);
	protected static final GroupingProperty grouping = new GroupingProperty();
	protected static final Property<Integer> randomIterationsProperty = new Property<>("random_iterations",
			Property.IntegerConverter);

	public static int YASA_MIN_SIZE;
	public static int YASA_MAX_SIZE;
	public static long YASA_MIN_TIME;
	public static long YASA_MAX_TIME;

	protected Path samplesDir, curSampleDir;

	public TWiseSampler(String configPath, String configName) throws Exception {
		super(configPath, configName);
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		extendCSVWriter(getModelCSVWriter(), Arrays.asList("Configurations", "FMFeatures", "FMConstraints", "FMPCs",
				"FMPCFeatures", "PCFeatures", "PCConstraints", "PCs"));
		extendCSVWriter(getDataCSVWriter(), Arrays.asList("Size"));
	}

	@Override
	protected List<Algorithm<SolutionList>> prepareAlgorithms() {
		final ArrayList<Algorithm<SolutionList>> algorithms = new ArrayList<>();

		for (final String algorithmName : algorithmsProperty.getValue()) {
			for (final String tValueString : tProperty.getValue()) {
				final int tValue = Integer.parseInt(tValueString);
				final Path sampleFile = config.tempPath.resolve("sample.csv");
				final Path modelFile = config.tempPath.resolve("model.dimacs");
				switch (algorithmName) {
				case "DUMMY": {
					algorithms.add(new Dummy());
					break;
				}
				case "IC": {
					algorithms.add(new ICPL(tValue, sampleFile, modelFile));
					break;
				}
				case "CH": {
					algorithms.add(new Chvatal(tValue, sampleFile, modelFile));
					break;
				}
				case "FIC": {
					algorithms.add(new FIDEICPL(tValue, sampleFile, modelFile));
					break;
				}
				case "FCH": {
					algorithms.add(new FIDEChvatal(tValue, sampleFile, modelFile));
					break;
				}
				case "IL": {
					if (tValue == 2) {
						final IncLing incLing = new IncLing(sampleFile, modelFile);
						incLing.setSeed(config.randomSeed.getValue());
						algorithms.add(incLing);
					}
					break;
				}
				case "PL": {
					algorithms.add(new PLEDGE_MIN(sampleFile, modelFile));
					algorithms.add(new PLEDGE_MAX(sampleFile, modelFile));
					break;
				}
				case "RND": {
					final FIDERandom fideRandom = new FIDERandom(sampleFile, modelFile);
					fideRandom.setIterations(randomIterationsProperty.getValue());
					final String systemName = config.systemNames.get(systemID);
					switch (systemName) {
					case "axtls":
						fideRandom.setLimit(50);
						break;
					case "fiasco":
						fideRandom.setLimit(40);
						break;
					case "toybox":
						fideRandom.setLimit(50);
						break;
					case "uclibc-ng":
						fideRandom.setLimit(600);
						break;
					case "busybox-1_29_2":
						fideRandom.setLimit(90);
						break;
					case "linux_2_6_28_6":
						fideRandom.setLimit(800);
						break;
					default:
//						fideRandom.setLimit(800);
//						throw new RuntimeException();
					}
					algorithms.add(fideRandom);
					break;
				}
				case "YA": {
					for (final String groupingValue : grouping.getValue()) {
						final Path expressionFile = config.tempPath
								.resolve("expressions_" + groupingValue + ".expression");
						for (final String mValue : mProperty.getValue()) {
							final YASA yasa = new YASA(sampleFile, modelFile);
							yasa.setT(tValue);
							yasa.setM(Integer.parseInt(mValue));
							yasa.setExpressionFile(expressionFile);
							yasa.setGroupingValue(groupingValue);
							yasa.setSeed(config.randomSeed.getValue());
							algorithms.add(yasa);
						}
					}
					break;
				}
				}
			}
		}

		return algorithms;
	}

	@Override
	protected CNF prepareModel() throws Exception {
		final String systemName = config.systemNames.get(systemID);

		final ModelReader<Formula> fmReader = new ModelReader<>();
		fmReader.setPathToFiles(config.modelPath);
		fmReader.setFormatSupplier(FormatSupplier.of(new DIMACSFormat()));
		final Result<CNF> fm = fmReader.read(systemName).map(Clauses::convertToCNF);

		final CNF modelCNF = fm.orElse(() -> {
			try {
				return PresenceConditionList.readPCList(systemName, Constants.convertedPCFileName).getFormula();
			} catch (final IOException e) {
				Logger.logError(e);
				return null;
			}
		});

		curSampleDir = samplesDir.resolve(String.valueOf(config.systemIDs.get(systemID)));
		Files.createDirectories(curSampleDir);
		final DIMACSFormatCNF format = new DIMACSFormatCNF();
		final Path fileName = curSampleDir.resolve("model." + format.getFileExtension());
		FileHandler.serialize(modelCNF, fileName, format);

		return modelCNF;
	}

	@Override
	protected CNF adaptModel() throws IOException {
		final CNF randomCNF = modelCNF.randomize(new Random(config.randomSeed.getValue() + systemIteration));
		final DIMACSFormatCNF format = new DIMACSFormatCNF();
		final Path fileName = config.tempPath.resolve("model" + "." + format.getFileExtension());
		FileHandler.serialize(randomCNF, fileName, format);

		for (final String groupingValue : grouping.getValue()) {
			try {
				saveExpressions(modelCNF, randomCNF, groupingValue);
			} catch (final Exception e) {
				Logger.logError(e);
			}
		}
//		saveExpressions(modelCNF1, randomCNF1, GroupingProperty.FM_ONLY);
//		saveExpressions(modelCNF1, randomCNF1, GroupingProperty.PC_ALL_FM);
//		saveExpressions(modelCNF1, randomCNF1, GroupingProperty.PC_ALL_FM_FM);
//		saveExpressions(modelCNF1, randomCNF1, GroupingProperty.PC_FOLDER_FM);
//		saveExpressions(modelCNF1, randomCNF1, GroupingProperty.PC_FILE_FM);
//		saveExpressions(modelCNF1, randomCNF1, GroupingProperty.PC_VARS_FM);
//
//		saveExpressions(modelCNF2, randomCNF2, GroupingProperty.PC_ALL);
//		saveExpressions(modelCNF2, randomCNF2, GroupingProperty.PC_FOLDER);
//		saveExpressions(modelCNF2, randomCNF2, GroupingProperty.PC_FILE);
//		saveExpressions(modelCNF2, randomCNF2, GroupingProperty.PC_VARS);

		YASA_MIN_SIZE = Integer.MAX_VALUE;
		YASA_MAX_SIZE = -1;
		YASA_MIN_TIME = Long.MAX_VALUE;
		YASA_MAX_TIME = -1;

		return randomCNF;
	}

	private void saveExpressions(final CNF cnf, final CNF randomCNF, String group) throws IOException {
		final Expressions readExpressions = readExpressions(config.systemNames.get(systemID), group);
		if (readExpressions != null) {
			final List<List<ClauseList>> expressionGroups = adaptConditions(cnf, randomCNF,
					readExpressions.getExpressions());
			randomizeConditions(expressionGroups, new Random(config.randomSeed.getValue() + systemIteration));

			final ExpressionGroupFormat format = new ExpressionGroupFormat();
			final Path fileName = config.tempPath.resolve("expressions_" + group + "." + format.getFileExtension());
			FileHandler.serialize(expressionGroups, fileName, format);
		}
	}

	@Override
	protected void adaptAlgorithm(Algorithm<SolutionList> algorithm) throws Exception {
		if (algorithm instanceof FIDERandom) {
			((FIDERandom) algorithm).setSeed(config.randomSeed.getValue() + algorithmIteration);
		} else if (algorithm instanceof PLEDGE_MIN) {
			((PLEDGE_MIN) algorithm).setTimeout(YASA_MIN_TIME);
		} else if (algorithm instanceof PLEDGE_MAX) {
			((PLEDGE_MAX) algorithm).setTimeout(YASA_MAX_TIME);
		}
	}

	@Override
	protected void writeModel(CSVWriter modelCSVWriter) {
		modelCSVWriter.addValue(config.systemIDs.get(systemID));
		modelCSVWriter.addValue(config.systemNames.get(systemID));

		final String systemName = config.systemNames.get(systemID);
		try {
			final PresenceConditionList pcfmList = PresenceConditionList.readPCList(systemName,
					Constants.convertedPCFMFileName);
			final CNF formula = pcfmList.getFormula();
			final CountSolutionsAnalysis countAnalysis = new CountSolutionsAnalysis();
			countAnalysis.setTimeout(0);
			modelCSVWriter.addValue(Executor.run(countAnalysis, formula).get());
			modelCSVWriter.addValue(formula.getVariableMap().size());
			modelCSVWriter.addValue(formula.getClauses().size());
			modelCSVWriter.addValue(pcfmList.size());
			modelCSVWriter.addValue(pcfmList.getPCNames().size());
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
			modelCSVWriter.addValue(0);
			modelCSVWriter.addValue(-1);
			modelCSVWriter.addValue(-1);
			modelCSVWriter.addValue(-1);
			modelCSVWriter.addValue(-1);
		}

		try {
			final PresenceConditionList pcList = PresenceConditionList.readPCList(systemName,
					Constants.convertedPCFileName);
			final CNF formula = pcList.getFormula();
			modelCSVWriter.addValue(formula.getVariableMap().size());
			modelCSVWriter.addValue(formula.getClauses().size());
			modelCSVWriter.addValue(pcList.size());
		} catch (final FileNotFoundException e) {
		} catch (final IOException e) {
			modelCSVWriter.addValue(-1);
			modelCSVWriter.addValue(-1);
			modelCSVWriter.addValue(-1);
		}
	}

	public Expressions readExpressions(String name, String grouping) {
		try {
			return Expressions.readConditions(name, Constants.groupedPCFileName + grouping);
		} catch (final IOException e) {
			Logger.logDebug(e.getMessage());
			return null;
		}
	}

	@Override
	protected void writeData(CSVWriter dataCSVWriter) {
		super.writeData(dataCSVWriter);
		final SolutionList configurationList = result.getResult();
		if (configurationList == null) {
			dataCSVWriter.addValue(-1);
			return;
		}
		dataCSVWriter.addValue(configurationList.getSolutions().size());

		writeSamples(config.systemIDs.get(systemID) + "_" + systemIteration + "_" + algorithmIndex + "_"
				+ algorithmIteration, configurationList.getSolutions());

		if (Objects.equals("YASA", algorithmList.get(algorithmIndex).getName())) {
			if (YASA_MAX_SIZE < configurationList.getSolutions().size()) {
				YASA_MAX_SIZE = configurationList.getSolutions().size();
			}
			if (YASA_MIN_SIZE > configurationList.getSolutions().size()) {
				YASA_MIN_SIZE = configurationList.getSolutions().size();
			}
			if (YASA_MAX_TIME < result.getTime()) {
				YASA_MAX_TIME = result.getTime();
			}
			if (YASA_MIN_TIME > result.getTime()) {
				YASA_MIN_TIME = result.getTime();
			}
		}

		Logger.logInfo("\t\tDone.");
	}

	@Override
	protected void setupDirectories() throws IOException {
		super.setupDirectories();

		samplesDir = config.outputPath.resolve("samples");
		Files.createDirectories(samplesDir);
	}

	protected List<List<ClauseList>> adaptConditions(CNF cnf, CNF randomCNF, List<List<ClauseList>> groupedConditions) {
		final ArrayList<List<ClauseList>> adaptedGroupedConditions = new ArrayList<>();
		for (final List<ClauseList> conditions : groupedConditions) {
			final ArrayList<ClauseList> adaptedConditions = new ArrayList<>();
			for (final ClauseList condition : conditions) {
				final ClauseList adaptedClauseList = new ClauseList();
				for (final LiteralList clause : condition) {
					adaptedClauseList.add(clause.adapt(cnf.getVariableMap(), randomCNF.getVariableMap()).get());
				}
				adaptedConditions.add(adaptedClauseList);
			}
			adaptedGroupedConditions.add(adaptedConditions);
		}
		return adaptedGroupedConditions;
	}

	protected void randomizeConditions(List<List<ClauseList>> groupedConditions, Random random) {
		for (final List<ClauseList> group : groupedConditions) {
			Collections.shuffle(group, random);
		}
		Collections.shuffle(groupedConditions, random);
	}

	protected void writeSamples(final String sampleMethod, final List<LiteralList> configurationList) {
		try {
			Files.write(curSampleDir.resolve(sampleMethod + ".sample"), //
					configurationList.stream() //
							.map(this::reorderSolution) //
							.map(TWiseSampler::toString) //
							.collect(Collectors.toList()));
		} catch (final IOException e) {
			Logger.logError(e);
		}
	}

	private LiteralList reorderSolution(LiteralList solution) {
		final LiteralList adaptedSolution = solution
				.adapt(randomizedModelCNF.getVariableMap(), modelCNF.getVariableMap()).get();
		adaptedSolution.setOrder(Order.INDEX);
		return adaptedSolution;
	}

	private static String toString(LiteralList literalSet) {
		final StringBuilder sb = new StringBuilder();
		for (final int literal : literalSet.getLiterals()) {
			sb.append(literal);
			sb.append(',');
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

}
