def board_list = [
[name: "MIMXRT1020_EVK", device_name: "MIMXRT1021DAG5A", serial_num: "A324F865D249154E", start_addr: "0x60000000", debugger: true, debugger_serial: "260106308"],
]

pipeline {
    agent {label 'devkit'}

    stages {
        stage('Prepare') {
            options {
                timeout(time: 40, unit: 'MINUTES')
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
                sh("git fetch --all --prune")
                sh("git reset --hard ${BRANCH}")
                sh("git submodule update --init")
            }
        }

        stage('Build - RELEASE') {
           options {
               timeout(time: 40, unit: 'MINUTES')
           }
           steps {
               dir('ports/mimxrt/') {
                   script {
                    if(params.get("REBUILD") == true) {
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

        stage('Functional Tests - RELEASE') {
            options {
                timeout(time: 25, unit: 'MINUTES')
            }
            failFast false
            parallel {
                stage('MIMXRT1020_EVK') {
                    steps{
                        dir('tools/') {
                            sh'''
                            TTY=$(ls -l /dev/serial/by-id | grep "A324F865D249154E" | grep -oP "tty\\w*\\d*")
                            python3.9 pyboard.py -d /dev/${TTY} ../../../scripts/sdcardtests.py
                            '''
                        }
                    }
                }
            }
        }
    }
}
