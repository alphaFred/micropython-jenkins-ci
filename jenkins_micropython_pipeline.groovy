def board_list = [
[name: "MIMXRT1020_EVK", device_name: "MIMXRT1021DAG5A", serial_num: "A324F865D249154E", start_addr: "0x60000000", debugger: true, debugger_serial: "260106308"],
[name: "SEEED_ARCH_MIX", device_name: "MIMXRT1052DVL6B", serial_num: "997EE163D2A92656", start_addr: "0x60000000", debugger: false, debugger_serial: ""],
[name: "TEENSY41"      , device_name: "MIMXRT1062DVJ6A", serial_num: "1CD0455DD7B1193F", start_addr: "0x60000000", debugger: false, debugger_serial: ""],
[name: "MIMXRT1010_EVK", device_name: "MIMXRT1011DAE5A", serial_num: "A8100D7BD7092D15", start_addr: "0x60000400", debugger: true, debugger_serial: "801038175"]
]

pipeline {
    agent {label 'devkit'}

    stages {
        stage('Prepare') {
            options {
                timeout(time: 5, unit: 'MINUTES')
            }
            steps {
                echo'Check test board availability ...'
                script{
                    board_list.each{ board ->
                        sh("dfu-util -l | grep \"${board['serial_num']}\" ")
                    }
                }

                echo'Setup repository ...'
                git 'https://github.com/alphaFred/micropython.git'
                sh 'git fetch --all --prune'
                sh 'git reset --hard origin/mimxrt/bootloader'
                sh 'git submodule update --init'
            }
        }

        stage('Build - DEBUG') {
           options {
               timeout(time: 40, unit: 'MINUTES')
           }
           steps {
               dir('ports/mimxrt/') {
                   script {
                    if(params.get("REBUILD_BINARIES") == true) {
                           board_list.each{ board ->
                               sh("make clean BOARD=${board['name']}")
                               sh("make all BOARD=${board['name']} DEBUG=1 -j4")
                           }
                       }
                   }
               }
           }
        }

        stage('Deploy - DEBUG') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            steps {
                dir('ports/mimxrt/') {
                    script{
                        board_list.findAll { board ->
                            board['debugger'] == true
                            }.each { board ->
                                sh("echo \"ExitOnError 1\" > build-${board['name']}/script.jlink")
                                sh("echo \"speed auto\" >> build-${board['name']}/script.jlink")
                                sh("echo \"r\" >> build-${board['name']}/script.jlink")
                                sh("echo \"st\" >> build-${board['name']}/script.jlink")
                                sh("echo \"loadfile \"/home/jenkins/workspace/MicroPython/ports/mimxrt/build-${board['name']}/firmware.bin\" ${board['start_addr']}\" >> build-${board['name']}/script.jlink")
                                sh("echo \"qc\" >> build-${board['name']}/script.jlink")
                                sh("/usr/bin/JLinkExe -device ${board['device_name']} -if SWD -USB ${board['debugger_serial']} -CommanderScript build-${board['name']}/script.jlink")
                                sh("sleep 4s")
                                sh("dfu-util -l | grep \"${board['serial_num']}\" | grep \"f055:9802\"")
                            }
                        }
                    }
                }
        }

        stage("Bootloader Tests - DEBUG") {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            failFast false
            parallel {
                stage('MIMXRT1020_EVK') {
                    steps {
                        script{
                            test_firmware_update("MIMXRT1020_EVK", "A324F865D249154E", 5)
                        }
                    }
                }
                stage('SEEED_ARCH_MIX') {
                    steps {
                        script{
                            test_firmware_update("SEEED_ARCH_MIX", "997EE163D2A92656", 5)
                        }
                    }
                }
                stage('TEENSY41') {
                    steps {
                        script{
                            test_firmware_update("TEENSY41", "1CD0455DD7B1193F", 5)
                        }
                    }
                }
                stage('MIMXRT1010_EVK') {
                    steps {
                        script{
                            test_firmware_update("MIMXRT1010_EVK", "A8100D7BD7092D15", 5)
                        }
                    }
                }
            }
        }

        stage('Functional Tests - DEBUG') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            failFast false
            parallel {
                stage('MIMXRT1020_EVK') {
                    steps{
                        dir('tests/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-MIMXRT1020_EVK/perfbench_results_MIMXRT1020_EVK.txt
                            '''
                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('SEEED ARCH MIX') {
                    steps{
                        dir('tests/') {

                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-SEEED_ARCH_MIX/perfbench_results_SEEED_ARCH_MIX.txt
                            '''
                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('TEENSY41') {
                    steps{
                        dir('tests/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-TEENSY41/perfbench_results_TEENSY41.txt
                            '''

                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('MIMXRT1010_EVK') {
                    steps{
                        dir('tests/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A8100D7BD7092D15" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-MIMXRT1010_EVK/perfbench_results_MIMXRT1010_EVK.txt
                            '''

                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A8100D7BD7092D15" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                        }
                    }
                }
            }
        }

        stage('Build - RELEASE') {
           options {
               timeout(time: 40, unit: 'MINUTES')
           }
           steps {
               dir('ports/mimxrt/') {
                   script {
                    if(params.get("REBUILD_BINARIES") == true) {
                           board_list.each{ board ->
                               sh("make clean BOARD=${board['name']}")
                               sh("make all BOARD=${board['name']} -j4")
                           }
                       }
                   }
               }
           }
        }

        stage('Deploy - RELEASE') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            steps {
                dir('ports/mimxrt/') {
                    script{
                        board_list.findAll { board ->
                            board['debugger'] == true
                            }.each { board ->
                                sh("echo \"ExitOnError 1\" > build-${board['name']}/script.jlink")
                                sh("echo \"speed auto\" >> build-${board['name']}/script.jlink")
                                sh("echo \"r\" >> build-${board['name']}/script.jlink")
                                sh("echo \"st\" >> build-${board['name']}/script.jlink")
                                sh("echo \"loadfile \"/home/jenkins/workspace/MicroPython/ports/mimxrt/build-${board['name']}/firmware.bin\" ${board['start_addr']}\" >> build-${board['name']}/script.jlink")
                                sh("echo \"qc\" >> build-${board['name']}/script.jlink")
                                sh("/usr/bin/JLinkExe -device ${board['device_name']} -if SWD -USB ${board['debugger_serial']} -CommanderScript build-${board['name']}/script.jlink")
                                sh("sleep 4s")
                                sh("dfu-util -l | grep \"${board['serial_num']}\" | grep \"f055:9802\"")
                            }
                        }
                    }
                }
        }

        stage("Bootloader Tests - RELEASE") {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            failFast false
            parallel {
                stage('MIMXRT1020_EVK') {
                    steps {
                        script{
                            test_firmware_update("MIMXRT1020_EVK", "A324F865D249154E", 5)
                        }
                    }
                }
                stage('SEEED_ARCH_MIX') {
                    steps {
                        script{
                            test_firmware_update("SEEED_ARCH_MIX", "997EE163D2A92656", 5)
                        }
                    }
                }
                stage('TEENSY41') {
                    steps {
                        script{
                            test_firmware_update("TEENSY41", "1CD0455DD7B1193F", 5)
                        }
                    }
                }
                stage('MIMXRT1010_EVK') {
                    steps {
                        script{
                            test_firmware_update("MIMXRT1010_EVK", "A8100D7BD7092D15", 5)
                        }
                    }
                }
            }
        }

        stage('Functional Tests - RELEASE') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            failFast false
            parallel {
                stage('MIMXRT1020_EVK') {
                    steps{
                        dir('tests/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-MIMXRT1020_EVK/perfbench_results_MIMXRT1020_EVK.txt
                            '''
                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('SEEED ARCH MIX') {
                    steps{
                        dir('tests/') {

                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-SEEED_ARCH_MIX/perfbench_results_SEEED_ARCH_MIX.txt
                            '''
                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "997EE163D2A92656" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('TEENSY41') {
                    steps{
                        dir('tests/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-TEENSY41/perfbench_results_TEENSY41.txt
                            '''

                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "1CD0455DD7B1193F" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
                stage('MIMXRT1010_EVK') {
                    steps{
                        dir('tests/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A8100D7BD7092D15" | grep -oP "tty\\w*\\d*")
                            python3.9 run-perfbench.py -d /dev/${TTY} -p 500000000 128 perf_bench/*viper* perf_bench/misc_aes.py perf_bench/bm_float.py > ../ports/mimxrt/build-MIMXRT1010_EVK/perfbench_results_MIMXRT1010_EVK.txt
                            '''

                        }
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A8100D7BD7092D15" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/vfstest.py
                            '''
                        }
                    }
                }
            }
        }

        stage('Archive') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                dir('ports/mimxrt/') {
                    archiveArtifacts artifacts: 'build-MIMXRT1020_EVK/*.map, build-MIMXRT1020_EVK/*.elf, build-MIMXRT1020_EVK/*.bin, build-MIMXRT1020_EVK/*.hex, build-MIMXRT1020_EVK/mboot/*.map, build-MIMXRT1020_EVK/mboot/*.bin, build-MIMXRT1020_EVK/mboot/*.hex, build-MIMXRT1020_EVK/perfbench_results_MIMXRT1020_EVK.txt', followSymlinks: false, onlyIfSuccessful: true
                    archiveArtifacts artifacts: 'build-SEEED_ARCH_MIX/*.map, build-SEEED_ARCH_MIX/*.elf, build-SEEED_ARCH_MIX/*.bin, build-SEEED_ARCH_MIX/*.hex, build-SEEED_ARCH_MIX/mboot/*.map, build-SEEED_ARCH_MIX/mboot/*.bin, build-SEEED_ARCH_MIX/mboot/*.hex, build-SEEED_ARCH_MIX/perfbench_results_SEEED_ARCH_MIX.txt', followSymlinks: false, onlyIfSuccessful: true
                    archiveArtifacts artifacts: 'build-TEENSY41/*.map, build-TEENSY41/*.elf, build-TEENSY41/*.bin, build-TEENSY41/*.hex, build-TEENSY41/mboot/*.map, build-TEENSY41/mboot/*.bin, build-TEENSY41/mboot/*.hex, build-TEENSY41/perfbench_results_TEENSY41.txt', followSymlinks: false, onlyIfSuccessful: true
                    archiveArtifacts artifacts: 'build-MIMXRT1010_EVK/*.map, build-MIMXRT1010_EVK/*.elf, build-MIMXRT1010_EVK/*.bin, build-MIMXRT1010_EVK/*.hex, build-MIMXRT1010_EVK/mboot/*.map, build-MIMXRT1010_EVK/mboot/*.bin, build-MIMXRT1010_EVK/mboot/*.hex, build-MIMXRT1010_EVK/perfbench_results_MIMXRT1010_EVK.txt', followSymlinks: false, onlyIfSuccessful: true
                }
            }
        }
    }
}

def test_firmware_update(String device_name, String device_serial, int n_times) {
    for(int i=0; i<n_times; i++) {
        dir('ports/mimxrt/') {
            sh("dfu-util -e -S ${device_serial}")
            sh("sleep 3s")
            sh("dfu-util -l | grep \"${device_serial}\" | grep \"cafe:4000\"")
            sh("sleep 1s")
            sh("dfu-util -D build-${device_name}/firmware.bin -S ${device_serial} -R")
            sh("sleep 4s")
            sh("dfu-util -l | grep \"${device_serial}\" | grep \"f055:9802\"")
        }

        dir('tests/') {
            sh("TTY=\$(ls -l /dev/serial/by-id | grep \"${device_serial}\" | grep -oP \"tty\\w*\\d*\");python3.9 run-perfbench.py -d /dev/\${TTY} -p 500000000 128 perf_bench/misc_aes.py")
        }
    }
}