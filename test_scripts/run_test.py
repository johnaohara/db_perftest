import logging
import os
import os.path
import re
import signal
import subprocess
import time
from datetime import datetime

test_duration = 10
primer_duration = 1
wait_after_primer = 1
wait_to_start = 10
wait_after_kill = 2
now = datetime.now()
javacmd = '/usr/lib/jvm/java-11-openjdk-amd64/bin/java'
wrkcmd = '/home/maarten/projects/wrk/wrk'
wrktimeout = '20s'

datestring = now.strftime("%Y%m%d_%H%M%S")
resultsfile = 'results_' + datestring + '.log'
outputfile = 'output_' + datestring + '.log'

logger = logging.getLogger('run_test')
logger.setLevel(logging.DEBUG)
# create console handler and set level to debug
ch = logging.StreamHandler()
ch.setLevel(logging.DEBUG)
fh = logging.FileHandler(outputfile)
fh.setLevel(logging.DEBUG)
# create formatter
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')

# add formatter to ch
ch.setFormatter(formatter)
fh.setFormatter(formatter)

# add ch to logger
logger.addHandler(ch)
logger.addHandler(fh)

cpuset_conf1 = ['3', '3,5', '3,5,7', '3,5,7,9', '3,5,7,9,11']
cpuset_conf2 = ['2', '2,4', '2,4,6', '2,4,6,8', '2,4,6,8,10']
concurrency_conf = ['1', '2', '4', '8', '16', '32', '64', '128', '256', '512', '1024', '2048']

# JAR files to test with
jarfiles = [{'filename': 'sb_jparest_hikari_jdbc-0.0.1-SNAPSHOT.jar', 'description': 'Spring Boot JPA REST JDBC',
             'driver': 'jdbc', 'pool': 'hikari', 'servlet_engine': 'tomcat', 'framework': 'JPA Data REST'},
            {'filename': 'sb_webflux_nopool_r2dbc-0.0.1-SNAPSHOT.jar',
             'description': 'Spring Boot WebFlux No pool R2DBC', 'driver': 'r2dbc', 'pool': 'none',
             'servlet_engine': 'netty', 'framework': 'Data'},
            {'filename': 'sb_webflux_r2dbcpool_r2dbc-0.0.1-SNAPSHOT.jar',
             'description': 'Spring Boot WebFlux R2DBC pool R2DBC', 'driver': 'r2dbc', 'pool': 'r2dbc',
             'servlet_engine': 'netty', 'framework': 'Data'}]


def check_prereqs():
    resval = True;
    for jarfile in jarfiles:
        if not os.path.isfile(jarfile.get('filename')):
            print('File not found: ' + jarfile.get('filename'))
            resval = False
    return resval


def build_jvmcmd(jar):
    return javacmd + ' ' + '-jar ' + jar


# Estimate test duration
def estimate_duration():
    total = 0
    for jarfile in jarfiles:
        for cpuset1 in cpuset_conf1:
            for cpuset2 in cpuset_conf2:
                for concurrency in concurrency_conf:
                    total = total + test_duration + primer_duration + wait_after_primer + wait_to_start
    return total / 60 / 60


# counts from a comma separated list the number of cpus
def get_cpu_num(cpuset):
    return len(cpuset.split(','))


def exec_all_tests():
    logger.info('Estimated duration: ' + str(estimate_duration()) + ' hours')
    logger.info('Using logfile: ' + resultsfile)
    # write header
    with open(resultsfile, 'a') as the_file:
        the_file.write('description,driver,pool,servlet_engine,framework,cpus_load,cpus_service,threads,concurrency\n')
    for jarfile in jarfiles:
        jvmcmd = build_jvmcmd(jarfile.get('filename'));
        jvm_outputline = jarfile.get('description') + ',' + jarfile.get('driver') + ',' + jarfile.get(
            'pool') + ',' + jarfile.get('servlet_engine') + ',' + jarfile.get('framework')
        logger.info('Processing: ' + jvm_outputline + ' using command: ' + jvmcmd)
        for cpuset_load in cpuset_conf1:
            cpunum_load = str(get_cpu_num(cpuset_load))
            logger.info('Number of CPUs for load generation' + cpunum_load)
            for cpuset_service in cpuset_conf2:
                cpunum_service = str(get_cpu_num(cpuset_service))
                logger.info('Number of CPUs for service' + cpunum_service)
                for concurrency in concurrency_conf:
                    logger.info('Number of concurrent requests ' + concurrency)
                    pid = start_java_process(jvmcmd, cpuset_service)
                    logger.info('Java process PID is: ' + pid)
                    if (len(str(pid)) == 0):
                        pid = start_java_process(jvmcmd, cpuset_service)
                        logger.warning('Retry startup. Java process PID is: ' + pid)
                        if (len(str(pid)) == 0):
                            pid = start_java_process(jvmcmd, cpuset_service)
                            logger.warning('Second retry startup. Java process PID is: ' + pid)
                    if (len(str(pid)) == 0 and len(str(get_java_process_pid())) > 0):
                        pid = get_java_process_pid()
                        logger.info('Setting new PID to ' + pid)
                    try:
                        output_primer = execute_test_single(cpuset_load, cpunum_load, concurrency, primer_duration)
                        time.sleep(wait_after_primer)
                        output_test = execute_test_single(cpuset_load, cpunum_load, concurrency, test_duration)
                        wrk_output = parse_wrk_output(output_test)
                        outputline = jvm_outputline + wrk_data(wrk_output)
                    except:
                        # Retry
                        logger.warning('Executing retry')
                        time.sleep(wait_to_start)
                        try:
                            output_primer = execute_test_single(cpuset_load, cpunum_load, concurrency, primer_duration)
                            time.sleep(wait_after_primer)
                            output_test = execute_test_single(cpuset_load, cpunum_load, concurrency, test_duration)
                            wrk_output = parse_wrk_output(output_test)
                            outputline = jvm_outputline + wrk_data(wrk_output)
                        except:
                            logger.warning("Giving up. Test failed. Writing FAILED to results file")
                            outputline = jvm_outputline + wrk_data_failed()
                    outputline = outputline + ',' + str(test_duration)
                    with open(resultsfile, 'a') as the_file:
                        the_file.write(outputline + '\n')
                    kill_process(pid)
    return


def wrk_data(wrk_output):
    return ',' + wrk_output.get('lat_avg') + ',' + wrk_output.get('lat_stdev') + ',' + wrk_output.get('lat_max') + ',' + wrk_output.get('req_avg') + ',' + wrk_output.get('req_stdev') + ',' + wrk_output.get('req_max') + ',' + wrk_output.get('tot_requests') + ',' + wrk_output.get('tot_duration') + ',' + wrk_output.get('read');


def wrk_data_failed():
    return ',FAILED,FAILED,FAILED,FAILED,FAILED,FAILED,FAILED,FAILED,FAILED';


def parse_wrk_output(wrk_output):
    retval = {}
    #  Thread Stats Avg        Stdev   Max       +/- Stdev
    #    Latency    58.53ms    6.42ms  69.83ms   62.50%
    #    Req/Sec    16.00      5.16    20.00     60.00%
    #  16 requests in 1.00s, 766.79KB read
    for line in wrk_output.splitlines():
        x = re.search("^\s+Latency\s+(\d+\.\d+)ms\s+(\d+\.\d+)ms\s+(\d+\.\d+)ms.*$", line)
        if x is not None:
            retval['lat_avg'] = x.group(1)
            retval['lat_stdev'] = x.group(2)
            retval['lat_max'] = x.group(3)
        x = re.search("^\s+Req/Sec\s+(\d+\.\d+)\s+(\d+\.\d+)\s+(\d+\.\d+).*$", line)
        if x is not None:
            retval['req_avg'] = x.group(1)
            retval['req_stdev'] = x.group(2)
            retval['req_max'] = x.group(3)
        x = re.search("^\s+(\d+)\ requests in (\d+\.\d+)s\,\ (\d+\.\d+\w+)\ read.*$", line)
        if x is not None:
            retval['tot_requests'] = x.group(1)
            retval['tot_duration'] = x.group(2)
            retval['read'] = x.group(3)
    return retval


def get_java_process_pid():
    cmd = 'ps -o pid,sess,cmd afx | egrep "( |/)java.*-SNAPSHOT.jar.*( -f)?$" | awk \'{print $1}\''
    output = subprocess.getoutput(cmd)
    return output


def start_java_process(java_cmd, cpuset):
    oldpid = get_java_process_pid()
    if (oldpid.isdecimal()):
        logger.info('Old Java process found with PID: ' + oldpid + '. Killing it')
        kill_process(oldpid)
    cmd = 'taskset -c ' + cpuset + ' ' + java_cmd
    subprocess.Popen(cmd.split(' '), stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    time.sleep(wait_to_start)
    return get_java_process_pid()


def execute_test_single(cpuset, threads, concurrency, duration):
    logger.info('Executing test with concurrency: ' + str(concurrency) + ' and duration ' + str(
        duration) + ' and threads ' + str(threads))
    cmd = 'taskset -c ' + str(cpuset) + ' ' + wrkcmd + '--latency --timeout ' + wrktimeout + ' -d' + str(
        duration) + 's -c' + str(concurrency) + ' -t' + str(threads) + ' http://localhost:8080/greeting?name=Maarten'
    process = subprocess.run(cmd.split(' '), check=True, stdout=subprocess.PIPE, universal_newlines=True)
    output = process.stdout
    logger.debug('Executing test command ' + cmd)
    logger.info('Executing test done')
    return output


def kill_process(pid):
    logger.info('Killing process with PID: ' + pid)
    try:
        os.kill(int(pid), signal.SIGKILL)
    except:
        logger.info('Process not found')
    try:
        # this will fail if the process is not a childprocess
        os.waitpid(int(pid), 0)
    except:
        # Just to be sure the process is really gone
        time.sleep(wait_after_kill)
    return


def main():
    if (not check_prereqs()):
        logger.error('Prerequisites not satisfied. Exiting')
        exit(1)
    else:
        logger.info('Prerequisites satisfied')
    print(exec_all_tests())
    logger.info('Test execution finished')


if __name__ == '__main__':
    main()