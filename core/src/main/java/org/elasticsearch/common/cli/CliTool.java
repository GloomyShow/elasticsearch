/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.cli;

import com.google.common.base.Preconditions;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.internal.InternalSettingsPreparer;

import java.io.IOException;
import java.util.Locale;

import static org.elasticsearch.common.settings.Settings.Builder.EMPTY_SETTINGS;

/**
 * A base class for command-line interface tool.
 * 命令行工具类接口
 *
 * Two modes are supported:
 * 支持两种模式
 *
 * - Single command mode. The tool exposes a single command that can potentially accept arguments (eg. CLI options).
 * - 单命令模式:该工具暴露了一个单独的命令可以潜在的接收参数(例如 CLI命令行 界面)
 * - Multi command mode. The tool support multiple commands, each for different tasks, each potentially accepts arguments.
 * - 多命令模式:该工具支持多个命令,对于每一个不同的任务,都可以接收参数.
 *
 * In a multi-command mode. The first argument must be the command name. For example, the plugin manager
 * can be seen as a multi-command tool with two possible commands: install and uninstall
 *
 * 在一个多命令模式中.第一个参数必须是命令名称.例如,插件管理工具可以被看做一个有两个可能的命令的多命令模式工具:安装和卸载.
 *
 *
 * The tool is configured using a {@link CliToolConfig} which encapsulates the tool's commands and their
 * potential options. The tool also comes with out of the box simple help support (the -h/--help option is
 * automatically handled) where the help text is configured in a dedicated *.help files located in the same package
 * as the tool.
 * 该工具是使用CliToolConfig ,用来封装工具的命令和她们潜在的选项.该工具还附带了开箱即用的简单帮助支持(使用-h/--help 选项),其中
 * 帮助文档是使用一个专用的.help文件位于该工具的同一个包中.
 *
 *
 */
public abstract class CliTool {

    // based on sysexits.h

    /**
     * 退出的状态码
     */
    public static enum ExitStatus {
        OK(0), //成功 0
        OK_AND_EXIT(0), // 成功并退出 0

        /**
         * 命令行出错 64
         */
        USAGE(64), /* command line usage error */

        /**
         * 日期格式化出错 65
         */
        DATA_ERROR(65),     /* data format error */

        /**
         * 无法打开输入 66
         */
        NO_INPUT(66),       /* cannot open input */

        /**
         * 位置地址 67
         */
        NO_USER(67),        /* addressee unknown */

        /**
         * 未知主机名 68
         */
        NO_HOST(68),        /* host name unknown */

        /**
         * 服务不可以用 69
         */
        UNAVAILABLE(69),    /* service unavailable */

        /**
         * 内部软件出错 70
         */
        CODE_ERROR(70),     /* internal software error */

        /**
         * 不能创建输出文件 73
         */
        CANT_CREATE(73),    /* can't create (user) output file */

        /**
         * IO错误 74
         */
        IO_ERROR(74),       /* input/output error */

        /**
         * 临时故障,用户被邀请重试 75
         */
        TEMP_FAILURE(75),   /* temp failure; user is invited to retry */

        /**
         * 远程网络协议出错 76
         */
        PROTOCOL(76),       /* remote error in protocol */

        /**
         * 没有权限 77
         */
        NOPERM(77),         /* permission denied */

        /**
         * 配置出错
         */
        CONFIG(78);          /* configuration error */

        final int status;

        private ExitStatus(int status) {
            this.status = status;
        }

        public int status() {
            return status;
        }

        public static ExitStatus fromStatus(int status) {
            for (ExitStatus exitStatus : values()) {
                if (exitStatus.status() == status) {
                    return exitStatus;
                }
            }

            return null;
        }
    }

    protected final Terminal terminal;
    protected final Environment env;
    protected final Settings settings;

    private final CliToolConfig config;

    protected CliTool(CliToolConfig config) {
        this(config, Terminal.DEFAULT);
    }

    protected CliTool(CliToolConfig config, Terminal terminal) {
        //如果cmd不存在,则报异常
        Preconditions.checkArgument(config.cmds().size() != 0, "At least one command must be configured");
        this.config = config;
        this.terminal = terminal;
        //加载环境变量
        env = InternalSettingsPreparer.prepareEnvironment(EMPTY_SETTINGS, terminal);
        settings = env.settings();
    }


    /**
     *
     * @param args
     * @return
     */
    public final ExitStatus execute(String... args) {

        // first lets see if the user requests tool help. We're doing it only if
        // this is a multi-command tool. If it's a single command tool, the -h/--help
        // option will be taken care of on the command level
        if (!config.isSingle() && args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
            //非单命令模式,并且args有只,并且为帮助选择
            config.printUsage(terminal);//打印帮助类
            return ExitStatus.OK_AND_EXIT;//返回OK并且退出的状态码
        }

        CliToolConfig.Cmd cmd;
        if (config.isSingle()) {
            cmd = config.single();
        } else {

            if (args.length == 0) {
                terminal.printError("command not specified");
                config.printUsage(terminal);
                return ExitStatus.USAGE;
            }

            String cmdName = args[0];
            cmd = config.cmd(cmdName);
            if (cmd == null) {
                terminal.printError("unknown command [%s]. Use [-h] option to list available commands", cmdName);
                return ExitStatus.USAGE;
            }

            // we now remove the command name from the args
            if (args.length == 1) {
                args = new String[0];
            } else {
                String[] cmdArgs = new String[args.length - 1];
                System.arraycopy(args, 1, cmdArgs, 0, cmdArgs.length);
                args = cmdArgs;
            }
        }

        Command command = null;
        try {

            command = parse(cmd, args);
            return command.execute(settings, env);
        } catch (IOException ioe) {
            terminal.printError(ioe);
            return ExitStatus.IO_ERROR;
        } catch (IllegalArgumentException ilae) {
            terminal.printError(ilae);
            return ExitStatus.USAGE;
        } catch (Throwable t) {
            terminal.printError(t);
            if (command == null) {
                return ExitStatus.USAGE;
            }
            return ExitStatus.CODE_ERROR;
        }
    }

    public Command parse(String cmdName, String[] args) throws Exception {
        CliToolConfig.Cmd cmd = config.cmd(cmdName);
        return parse(cmd, args);
    }

    public Command parse(CliToolConfig.Cmd cmd, String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        CommandLine cli = parser.parse(CliToolConfig.OptionsSource.HELP.options(), args, true);
        if (cli.hasOption("h")) {
            return helpCmd(cmd);
        }
        cli = parser.parse(cmd.options(), args, cmd.isStopAtNonOption());
        Terminal.Verbosity verbosity = Terminal.Verbosity.resolve(cli);
        terminal.verbosity(verbosity);
        return parse(cmd.name(), cli);
    }

    protected Command.Help helpCmd(CliToolConfig.Cmd cmd) {
        return new Command.Help(cmd, terminal);
    }

    protected static Command.Exit exitCmd(ExitStatus status) {
        return new Command.Exit(null, status, null);
    }

    protected static Command.Exit exitCmd(ExitStatus status, Terminal terminal, String msg, Object... args) {
        return new Command.Exit(String.format(Locale.ROOT, msg, args), status, terminal);
    }

    protected abstract Command parse(String cmdName, CommandLine cli) throws Exception;

    public static abstract class Command {

        protected final Terminal terminal;

        protected Command(Terminal terminal) {
            this.terminal = terminal;
        }

        public abstract ExitStatus execute(Settings settings, Environment env) throws Exception;

        public static class Help extends Command {

            private final CliToolConfig.Cmd cmd;

            private Help(CliToolConfig.Cmd cmd, Terminal terminal) {
                super(terminal);
                this.cmd = cmd;
            }

            @Override
            public ExitStatus execute(Settings settings, Environment env) throws Exception {
                cmd.printUsage(terminal);
                return ExitStatus.OK_AND_EXIT;
            }
        }

        public static class Exit extends Command {
            private final String msg;
            private final ExitStatus status;

            private Exit(String msg, ExitStatus status, Terminal terminal) {
                super(terminal);
                this.msg = msg;
                this.status = status;
            }

            @Override
            public ExitStatus execute(Settings settings, Environment env) throws Exception {
                if (msg != null) {
                    if (status != ExitStatus.OK) {
                        terminal.printError(msg);
                    } else {
                        terminal.println(msg);
                    }
                }
                return status;
            }

            public ExitStatus status() {
                return status;
            }
        }
    }



}

