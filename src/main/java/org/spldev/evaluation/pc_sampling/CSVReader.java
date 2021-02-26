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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.spldev.evaluation.Evaluator;
import org.spldev.formula.VariableMap;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.ClauseList;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.LiteralList.Order;
import org.spldev.formula.clause.configuration.twise.PresenceConditionManager;
import org.spldev.formula.clause.io.DIMACSFormat;
import org.spldev.formula.expression.Expression;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.Formulas;
import org.spldev.formula.expression.atomic.literal.Literal;
import org.spldev.formula.expression.compound.And;
import org.spldev.formula.expression.io.parse.NodeReader;
import org.spldev.formula.expression.io.parse.NodeReader.ErrorHandling;
import org.spldev.formula.expression.io.parse.Symbols;
import org.spldev.formula.expression.io.parse.Symbols.Operator;
import org.spldev.tree.Trees;
import org.spldev.tree.visitor.TreePrinter;
import org.spldev.util.Result;
import org.spldev.util.io.FileHandler;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.logging.Logger;

public class CSVReader extends Evaluator {

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("Configuration path and name not specified!");
			return;
		}
		final CSVReader evaluator = new CSVReader(args[0], args[1]);
		evaluator.init();
		evaluator.run();
		evaluator.dispose();
	}

	protected CSVWriter evaluationWriter;

	private CNF modelCNF;

	private List<int[]> sampleArguments;

	private final HashMap<String, List<PC>> map = new HashMap<>();

	private class PC {
		Formula formula;
		String formulaString;
	}

	public CSVReader(String configPath, String configName) throws Exception {
		super(configPath, configName);
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		evaluationWriter = addCSVWriter("coverage.csv", Arrays.asList("ModelID", "ModelName", "SystemIteration",
				"AlgorithmID", "AlgorithmIteration", "PresenceCondition", "Covered"));
	}

	protected final HashMap<String, PresenceConditionManager> expressionMap = new HashMap<>();

	@Override
	public void run() {
		super.run();

		final NodeReader nodeReader = new NodeReader();
		final Symbols symbols = new Symbols();
		symbols.setSymbol(Operator.NOT, "!");
		symbols.setSymbol(Operator.AND, "&&");
		symbols.setSymbol(Operator.OR, "||");
		symbols.setTextual(false);
		nodeReader.setSymbols(symbols);
		nodeReader.setIgnoreMissingFeatures(ErrorHandling.REMOVE);
		nodeReader.setIgnoreUnparsableSubExpressions(ErrorHandling.REMOVE);

		final Path p = Paths.get("bugs.csv");
		List<String> lines;
		try {
			lines = Files.readAllLines(p);

			for (final String line : lines) {
				final String[] values = line.split(";");
				final String systemName = values[0];
				final String formulaString = values[4];
				System.out.println(systemName);
				final Formula formula = nodeReader.read(formulaString).map(Formulas::toDNF).orElse(Logger::logProblems);
				System.out.println(Trees.traverse(formula, new TreePrinter()).get());
				List<PC> list = map.get(systemName);
				if (list == null) {
					list = new ArrayList<>();
					map.put(systemName, list);
				}
				final PC pc = new PC();
				pc.formula = formula;
				pc.formulaString = formulaString;
				list.add(pc);
			}
		} catch (final IOException e) {
			Logger.logError(e);
			return;
		}

		tabFormatter.setTabLevel(0);
		if (config.systemIterations.getValue() > 0) {
			Logger.logInfo("Start");
			tabFormatter.incTabLevel();

			final Path samplesDir = config.outputPath.resolve("samples");
			List<Path> dirList;
			try (Stream<Path> fileStream = Files.list(samplesDir)) {
				dirList = fileStream.filter(Files::isReadable).filter(Files::isDirectory).collect(Collectors.toList());
			} catch (final IOException e) {
				Logger.logError(e);
				return;
			}
			Collections.sort(dirList, (p1, p2) -> p1.getFileName().toString().compareTo(p2.getFileName().toString()));

			tabFormatter.incTabLevel();
			dirList.forEach(this::readSamples);
			tabFormatter.setTabLevel(0);
			Logger.logInfo("Finished");
		} else {
			Logger.logInfo("Nothing to do");
		}
	}

	private void readSamples(Path sampleDir) {
		tabFormatter.setTabLevel(1);
		try {
			systemID = Integer.parseInt(sampleDir.getFileName().toString());
		} catch (final Exception e) {
			Logger.logError(e);
			return;
		}

		final String systemName = config.systemNames.get(config.systemIDs.indexOf(systemID));
		Logger.logInfo("System " + (systemID + 1) + ": " + systemName);
		Logger.logInfo("Preparing...");
		tabFormatter.incTabLevel();

		final DIMACSFormat format = new DIMACSFormat();
		final Path modelFile = sampleDir.resolve("model." + format.getFileExtension());
		final Result<CNF> parseResult = FileHandler.parse(modelFile, format);
		if (parseResult.isEmpty()) {
			Logger.logProblems(parseResult.getProblems());
			return;
		}

		modelCNF = parseResult.get();

		List<Path> sampleFileList;
		try (Stream<Path> fileStream = Files.list(sampleDir)) {
			sampleFileList = fileStream.filter(Files::isReadable).filter(Files::isRegularFile)
					.filter(file -> file.getFileName().toString().endsWith(".sample")).collect(Collectors.toList());
		} catch (final IOException e) {
			Logger.logError(e);
			tabFormatter.decTabLevel();
			return;
		}
		Collections.sort(sampleFileList,
				(p1, p2) -> p1.getFileName().toString().compareTo(p2.getFileName().toString()));

		tabFormatter.decTabLevel();
		Logger.logInfo("Reading Samples...");
		tabFormatter.incTabLevel();
		final List<List<? extends LiteralList>> samples = new ArrayList<>(sampleFileList.size());
		sampleArguments = new ArrayList<>(sampleFileList.size());
		for (final Path sampleFile : sampleFileList) {
			final List<LiteralList> sample;
			int[] argumentValues;
			try {
				final String fileName = sampleFile.getFileName().toString();
				final String[] arguments = fileName.substring(0, fileName.length() - ".sample".length()).split("_");
				sample = Files.lines(sampleFile).map(this::parseConfiguration).collect(Collectors.toList());

				argumentValues = new int[3];
				argumentValues[0] = Integer.parseInt(arguments[1]);
				argumentValues[1] = Integer.parseInt(arguments[2]);
				argumentValues[2] = Integer.parseInt(arguments[3]);

			} catch (final Exception e) {
				Logger.logError(e);
				continue;
			}
			samples.add(sample);
			sampleArguments.add(argumentValues);
		}
		final List<PC> list = map.get(systemName);
		if (list != null) {
			tabFormatter.incTabLevel();
			for (final PC pc : list) {
				CNF dnf = null;
				try {
					dnf = toCNF(pc.formula, modelCNF.getVariableMap());
					Logger.logInfo(dnf.getClauses());
				} catch (final Exception e) {
					Logger.logError(e.getMessage());
				}
				tabFormatter.incTabLevel();
				int i = 0;
				for (final List<? extends LiteralList> sample : samples) {
					final int[] args = sampleArguments.get(i++);
					evaluationWriter.createNewLine();
					evaluationWriter.addValue(systemID);
					evaluationWriter.addValue(systemName);
					evaluationWriter.addValue(args[0]);
					evaluationWriter.addValue(args[1]);
					evaluationWriter.addValue(args[2]);
					evaluationWriter.addValue(pc.formulaString);
					if (dnf == null) {
						evaluationWriter.addValue("unparsable");
					} else {
						boolean covered = false;
						loop: for (final LiteralList configuration : sample) {
							for (final LiteralList clause : dnf.getClauses()) {
								if (configuration.containsAll(clause)) {
									covered = true;
									break loop;
								}
							}
						}
						evaluationWriter.addValue(covered);
					}
					evaluationWriter.flush();
				}
				tabFormatter.decTabLevel();
			}
			tabFormatter.decTabLevel();
		}
		tabFormatter.decTabLevel();
	}

	public CNF toCNF(Formula cnf, VariableMap mapping) {
		final ClauseList clauses = new ClauseList();
		if (cnf instanceof Literal) {
			cnf = new And(cnf);
		}
		cnf.getChildren().stream().map(exp -> getClause(exp, mapping)).filter(Objects::nonNull).forEach(clauses::add);
		return new CNF(mapping, clauses);
	}

	private LiteralList getClause(Expression clauseExpression, VariableMap mapping) {
		if (clauseExpression instanceof Literal) {
			final Literal literal = (Literal) clauseExpression;
			final int variable = mapping.getIndex(literal.getName())
					.orElseThrow(() -> new RuntimeException(literal.getName()));
			return new LiteralList(new int[] { literal.isPositive() ? variable : -variable }, Order.NATURAL);
		} else {
			final List<? extends Expression> clauseChildren = clauseExpression.getChildren();
			if (clauseChildren.stream().anyMatch(literal -> literal == Literal.True)) {
				return null;
			} else {
				final int[] literals = clauseChildren.stream().filter(literal -> literal != Literal.False)
						.mapToInt(literal -> {
							final int variable = mapping.getIndex(literal.getName())
									.orElseThrow(() -> new RuntimeException(literal.getName()));
							return ((Literal) literal).isPositive() ? variable : -variable;
						}).toArray();
				return new LiteralList(literals, Order.NATURAL);
			}
		}
	}

	private LiteralList parseConfiguration(String configuration) {
		final String[] literalStrings = configuration.split(",");
		final int[] literals = new int[literalStrings.length];
		for (int i = 0; i < literalStrings.length; i++) {
			literals[i] = Integer.parseInt(literalStrings[i]);
		}
		final LiteralList solution = new LiteralList(literals, Order.INDEX, false);
		return solution;
	}

}
