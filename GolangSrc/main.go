package nettools

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"syscall"
	"time"
)

var logger = log.New(os.Stderr, "\u001b[31mERROR: \u001b[0m", log.LstdFlags|log.Lshortfile)

type (
	Tracetool struct {
		start              bool
		pingResultCallback PingResultCallback
		onErrorCallback    OnErrorCallback
		pingTool           PingTool
	}
	PingTool struct {
		Process *os.Process
	}

	PingResult struct {
		IpLookupStatus bool
		IpAddress      string
		Delay          string
		Complated      bool
		IpInfo         *IpInfo
	}
	IpInfo struct {
		RegionName string `json:"regionName"`
		City       string `json:"city"`
		Country    string `json:"country"`
		Isp        string `json:"isp"`
		Org        string `json:"org"`
		Status     string `json:"status"`
	}
	readerOutput struct {
		Out   string
		delay int64
		err   error
	}
	PingResultCallback interface {
		Run(result *PingResult)
	}

	IpLookUpResultCallback interface {
		Run(result *IpInfo)
	}
	OnErrorCallback interface {
		Run(err error)
	}
	OnTraceStopped interface {
		Run(intercepted bool)
	}
	//isp,city,org,country,status,regionName
)

var command = map[bool]string{true: "/system/bin/ping6", false: "/system/bin/ping"}
var ipLens = map[bool]int{true: net.IPv6len, false: net.IPv4len}

func NewTracetool(pingResultCallback PingResultCallback, onErrorCallback OnErrorCallback) *Tracetool {
	return &Tracetool{pingResultCallback: pingResultCallback, onErrorCallback: onErrorCallback, pingTool: PingTool{}}
}

func readOutput(reader io.Reader, out chan *readerOutput) {
	scanner := bufio.NewReader(reader)
	start := time.Now()
	_, err := scanner.ReadString('a')
	if err != nil {
		out <- &readerOutput{err: err}
		return
	}
	_, err = scanner.ReadString('\n')
	delayNano := time.Since(start).Nanoseconds()
	if err != nil {
		out <- &readerOutput{err: err}
		return
	}
	outStr, err := scanner.ReadString('\n')

	if err != nil {
		out <- &readerOutput{err: err}
		return
	}
	out <- &readerOutput{Out: outStr, delay: delayNano}
}

func (p *PingTool) Ping(ipAddress string, ttl byte, isIpv6 bool) (*PingResult, error) {
	var result *PingResult
	cmd := exec.Command(command[isIpv6], "-c1", fmt.Sprintf("-t%d", ttl), ipAddress)
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, err
	}
	readerOutputChan := make(chan *readerOutput, 1)
	go readOutput(stdout, readerOutputChan)
	if err = cmd.Start(); err != nil {
		return nil, err
	}
	p.Process = cmd.Process
	if err = cmd.Wait(); err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			switch exitErr.ExitCode() {
			case 1:
				readerOut := <-readerOutputChan
				close(readerOutputChan)
				if readerOut.err != nil {
					logger.Println(err)
					return nil, readerOut.err
				}
				outputs := strings.Split(readerOut.Out, " ")
				if len(outputs) < 2 {
					logger.Println("output less then 2 line")
					return nil, nil
				}
				result = &PingResult{IpLookupStatus: true, Complated: false}
				delayMilli := float64(readerOut.delay) / float64(time.Millisecond.Nanoseconds())
				if delayMilli < 10 {
					result.Delay = fmt.Sprintf("%.2f", delayMilli)
				} else if delayMilli < 100 {
					result.Delay = fmt.Sprintf("%.1f", delayMilli)
				} else {
					result.Delay = fmt.Sprintf("%.0f", delayMilli)
				}
				if isIpv6 {
					result.IpAddress = outputs[1]
				} else {
					result.IpAddress = outputs[1][:len(outputs[1])-1]
				}
				return result, nil
			case 2:
				if isIpv6 {
					return nil, errors.New("noNet6")
				} else {
					return nil, errors.New("noNet")
				}
			}
		}
		logger.Println(err)
		close(readerOutputChan)
		return nil, err
	}
	readerOut := <-readerOutputChan
	close(readerOutputChan)
	if readerOut.err != nil {
		logger.Println(err)

		return nil, readerOut.err
	}
	result = &PingResult{IpLookupStatus: true, Complated: true}
	outputs := strings.Split(readerOut.Out[14:], " ")
	result.IpAddress = outputs[0][:len(outputs[0])-1]
	delayStr := outputs[3][5:]
	result.Delay = strings.TrimSpace(delayStr)
	return result, nil
}

func (t *Tracetool) Stop() error {
	t.start = false
	if t.pingTool.Process == nil {
		return nil
	}
	if err := t.pingTool.Process.Signal(syscall.SIGINT); err != nil {
		logger.Println(err)
		return err
	}
	return nil
}

func (t *Tracetool) TracertRoute(hostname string, isIpv6 bool) {
	go t.tracertRoute(hostname, isIpv6)
}

func (t *Tracetool) tracertRoute(hostname string, isIpv6 bool) {
	t.start = true
	ips, err := net.LookupIP(hostname)
	if err != nil {
		if dnsErr, ok := err.(*net.DNSError); ok {
			if dnsErr.IsNotFound {
				t.onErrorCallback.Run(errors.New("noHost"))
				return
			}
		}
		log.Println("Could not get IPs for ", hostname, err)
		t.onErrorCallback.Run(err)
		return
	}
	ipAddress := ""
	ipLen := ipLens[isIpv6]
	for _, ip := range ips {
		if len(ip) == ipLen {
			ipAddress = ip.String()
			break
		}
	}
	if ipAddress == "" {
		if isIpv6 {
			t.onErrorCallback.Run(errors.New("noIPv6"))
		} else {
			t.onErrorCallback.Run(errors.New("noIPv4"))
		}
		return
	}
	for i := 1; (i < 65) && t.start; i++ {
		ttl := byte(i)
		result, err := t.pingTool.Ping(ipAddress, ttl, isIpv6)
		if err != nil {
			t.onErrorCallback.Run(err)
			return
		}
		t.pingResultCallback.Run(result)
		if result != nil && result.Complated {
			return
		}

	}
	if t.start {
		t.onErrorCallback.Run(errors.New("unreachable"))
	} else {
		t.onErrorCallback.Run(errors.New("stoped"))
	}

}
func (r *PingResult) LookupIpInfo(isForce bool, cb IpLookUpResultCallback, ecb OnErrorCallback) {
	if !isForce && r.IpInfo != nil {
		cb.Run(r.IpInfo)
		return
	}
	go r.lookupIpInfo(cb, ecb)
}
func (r *PingResult) lookupIpInfo(cb IpLookUpResultCallback, ecb OnErrorCallback) {
	res, err := http.Get(fmt.Sprintf("http://ip-api.com/json/%s?fields=isp,city,org,country,status,regionName", r.IpAddress))
	if err != nil {
		logger.Println(err)
		ecb.Run(err)
		return
	}
	info := &IpInfo{}
	decoder := json.NewDecoder(res.Body)
	if err := decoder.Decode(info); err != nil {
		ecb.Run(err)
		return
	}
	if info.Status != "success" {
		r.IpLookupStatus = false
		err = errors.New("Error look up IP")
		ecb.Run(err)
		return
	}
	r.IpInfo = info
	cb.Run(info)
}
